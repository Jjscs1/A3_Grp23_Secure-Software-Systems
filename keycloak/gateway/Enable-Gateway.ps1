# Run only after Test-Gateway.ps1 passes. Existing Keycloak is retained for rollback.
$ErrorActionPreference = 'Stop'
$gatewayImage = 'openresty/openresty@sha256:8e223fc9644c0b12de445165ecc6161786d9f6aa1064ea5e77c9c88ba5d494b6'
$networkName = 'evp-keycloak-sec09'
$originalName = 'evp-keycloak'
$internalName = 'evp-keycloak-internal'
$gatewayName = 'evp-keycloak-gateway'
$gatewayMount = "type=bind,source=$PSScriptRoot,target=/etc/evp-gateway,readonly"

function Invoke-DockerChecked([string[]]$DockerArgs) {
    & docker @DockerArgs | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Docker operation failed: $($DockerArgs[0])"
    }
}

$originalJson = & docker inspect $originalName
if ($LASTEXITCODE -ne 0) {
    throw 'Original evp-keycloak container was not found.'
}

$original = @($originalJson | ConvertFrom-Json)[0]

if ($original.Config.Image -ne 'quay.io/keycloak/keycloak:26.8.1') {
    throw 'Unexpected Keycloak image. Stop and review.'
}

$expectedMount = @(
    $original.Mounts | Where-Object {
        $_.Type -eq 'volume' -and
        $_.Name -eq 'evp-keycloak-data' -and
        $_.Destination -eq '/opt/keycloak/data'
    }
)

if ($expectedMount.Count -ne 1) {
    throw 'Expected data volume was not found. Stop and review.'
}

if (-not $original.State.Running) {
    throw 'Start the original evp-keycloak container before using this migration script.'
}

$existingNames = @(& docker ps -a --format '{{.Names}}')
if ($LASTEXITCODE -ne 0) {
    throw 'Cannot list containers.'
}

if ($existingNames -contains $internalName -or $existingNames -contains $gatewayName) {
    throw 'A gateway/internal container already exists. Do not overwrite it; inspect first.'
}

$existingNetworks = @(& docker network ls --format '{{.Name}}')
if ($LASTEXITCODE -ne 0) {
    throw 'Cannot list networks.'
}

if ($existingNetworks -contains $networkName) {
    throw 'Migration network already exists. Inspect it before retrying.'
}

# Refuse to pull anything while Keycloak is stopped. The image must already be local.
& docker image inspect $gatewayImage --format '{{.Id}}' | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw 'Pinned gateway image is not available locally.'
}

& "$PSScriptRoot\Test-Gateway.ps1"

$backupRoot = Join-Path $env:USERPROFILE '.evp-demo'
New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null

$backupName = 'keycloak-before-sec09-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.tar.gz'
$backupMount = "type=bind,source=$backupRoot,target=/backup"

$networkCreated = $false
$originalStopped = $false
$internalCreated = $false
$gatewayCreated = $false
$lastDiscoveryError = 'Discovery was not attempted.'

$diagnosticRoot = Join-Path $backupRoot ('gateway-diagnostics-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))

function Save-GatewayDiagnostics {
    # Diagnostics are local only. Never print Config.Env or copy the database into Git.
    # Native stderr must not interrupt rollback in Windows PowerShell 5.1.
    $savedPreference = $ErrorActionPreference

    try {
        $ErrorActionPreference = 'Continue'
        New-Item -ItemType Directory -Force -Path $diagnosticRoot | Out-Null

        $lastDiscoveryError |
            Set-Content -Encoding UTF8 (Join-Path $diagnosticRoot 'discovery.txt')

        foreach ($containerName in @($internalName, $gatewayName)) {
            & docker inspect --format 'Status={{.State.Status}} ExitCode={{.State.ExitCode}} OOMKilled={{.State.OOMKilled}} Error={{.State.Error}}' $containerName 2>&1 |
                ForEach-Object { "$_" } |
                Set-Content -Encoding UTF8 (Join-Path $diagnosticRoot ($containerName + '-state.txt'))

            & docker logs --tail 120 $containerName 2>&1 |
                ForEach-Object { "$_" } |
                Set-Content -Encoding UTF8 (Join-Path $diagnosticRoot ($containerName + '-logs.txt'))
        }

        Write-Host "Diagnostic files saved locally: $diagnosticRoot"
        Write-Host "Last discovery result: $lastDiscoveryError"
    } catch {
        Write-Host 'Could not save all diagnostics. Continuing rollback.'
    } finally {
        $ErrorActionPreference = $savedPreference
    }
}

try {
    Invoke-DockerChecked @('network', 'create', $networkName)
    $networkCreated = $true

    Write-Host 'Stopping Keycloak briefly to take a consistent data-volume backup...'
    Invoke-DockerChecked @('stop', '--timeout', '30', $originalName)
    $originalStopped = $true

    Invoke-DockerChecked @(
        'run', '--rm',
        '--mount', 'type=volume,source=evp-keycloak-data,target=/source,readonly',
        '--mount', $backupMount,
        '--entrypoint', '/bin/sh',
        $gatewayImage,
        '-c',
        ('umask 077; tar -czf /backup/' + $backupName + ' -C /source . && tar -tzf /backup/' + $backupName + ' > /dev/null')
    )

    if (-not (Test-Path (Join-Path $backupRoot $backupName))) {
        throw 'Backup archive was not created.'
    }

    Write-Host "Backup saved outside Git: $(Join-Path $backupRoot $backupName)"

    # No published port on Keycloak: external browser traffic must pass through the gateway.
    Invoke-DockerChecked @(
        'create',
        '--name', $internalName,
        '--network', $networkName,
        '--mount', 'type=volume,source=evp-keycloak-data,target=/opt/keycloak/data',
        'quay.io/keycloak/keycloak:26.8.1',
        'start-dev',
        '--hostname=http://localhost:8081',
        '--proxy-headers=xforwarded'
    )

    $internalCreated = $true
    Invoke-DockerChecked @('start', $internalName)

    Invoke-DockerChecked @(
        'create',
        '--name', $gatewayName,
        '--network', $networkName,
        '-p', '127.0.0.1:8081:8080',
        '--mount', $gatewayMount,
        '--entrypoint', '/usr/local/openresty/bin/openresty',
        $gatewayImage,
        '-c', '/etc/evp-gateway/nginx.conf',
        '-g', 'daemon off;'
    )

    $gatewayCreated = $true
    Invoke-DockerChecked @('start', $gatewayName)

    $ready = $false

    for ($attempt = 1; $attempt -le 90; $attempt++) {
        try {
            $evp = Invoke-RestMethod 'http://127.0.0.1:8081/realms/evp/.well-known/openid-configuration' -TimeoutSec 5
            $staff = Invoke-RestMethod 'http://127.0.0.1:8081/realms/evp-staff/.well-known/openid-configuration' -TimeoutSec 5

            if ($evp.issuer -eq 'http://localhost:8081/realms/evp' -and $staff.issuer -eq 'http://localhost:8081/realms/evp-staff') {
                $ready = $true
                break
            }

            $lastDiscoveryError = "Unexpected issuers: evp=$($evp.issuer); staff=$($staff.issuer)"
        } catch {
            $lastDiscoveryError = $_.Exception.Message
        }

        if ($attempt % 10 -eq 0) {
            Write-Host "Waiting for Keycloak startup... ($lastDiscoveryError)"

            $internalState = & docker inspect --format '{{.State.Status}}' $internalName
            $gatewayState = & docker inspect --format '{{.State.Status}}' $gatewayName

            if ($internalState -ne 'running' -or $gatewayState -ne 'running') {
                throw "A new container stopped: Keycloak=$internalState; gateway=$gatewayState"
            }
        }

        Start-Sleep -Seconds 1
    }

    if (-not $ready) {
        throw 'Both realm discovery endpoints did not become ready.'
    }

    Write-Host 'Gateway is ready on http://localhost:8081. Both realm issuers are unchanged.'
    Write-Host 'Original evp-keycloak is STOPPED and retained. Never run both Keycloak containers together.'
    Write-Host 'Next: verify voter and staff sign-in, then HTTP 429 and recovery.'
} catch {
    $migrationError = $_

    Save-GatewayDiagnostics

    Write-Host 'Migration failed. Attempting rollback to the original container...'

    if ($gatewayCreated) {
        & docker rm -f $gatewayName | Out-Host
    }

    if ($internalCreated) {
        & docker stop --timeout 30 $internalName | Out-Host

        if ($LASTEXITCODE -ne 0) {
            throw 'Could not stop internal Keycloak. Original was NOT restarted, to protect the shared database.'
        }

        & docker rm $internalName | Out-Host
    }

    if ($originalStopped) {
        Invoke-DockerChecked @('start', $originalName)
    }

    if ($networkCreated) {
        & docker network rm $networkName | Out-Host
    }

    throw $migrationError
}