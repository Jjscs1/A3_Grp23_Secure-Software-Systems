# Return to the original port mapping without deleting data or the backup archive.
$ErrorActionPreference = 'Stop'
$names = @(& docker ps -a --format '{{.Names}}')
if ($LASTEXITCODE -ne 0) { throw 'Cannot list containers.' }
if ($names -notcontains 'evp-keycloak') { throw 'Original container is missing. Stop and inspect.' }
if ($names -contains 'evp-keycloak-gateway') {
    & docker stop --time 15 evp-keycloak-gateway
    if ($LASTEXITCODE -ne 0) { throw 'Cannot stop gateway. Stop and inspect.' }
}
if ($names -contains 'evp-keycloak-internal') {
    & docker stop --time 30 evp-keycloak-internal
    if ($LASTEXITCODE -ne 0) { throw 'Internal Keycloak could not be stopped. Do NOT start the original.' }
}
& docker start evp-keycloak
if ($LASTEXITCODE -ne 0) { throw 'Original container did not start. Inspect its logs.' }
Write-Host 'Original Keycloak restarted. Gateway protection is now disabled.'
Write-Host 'Stopped gateway/internal containers remain available for inspection.'
