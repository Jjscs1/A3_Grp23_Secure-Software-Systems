$ErrorActionPreference = 'Stop'
$gatewayImage = 'openresty/openresty@sha256:8e223fc9644c0b12de445165ecc6161786d9f6aa1064ea5e77c9c88ba5d494b6'
$gatewayMount = "type=bind,source=$PSScriptRoot,target=/etc/evp-gateway,readonly"

# The Alpine runtime does not include Perl, which the resty CLI requires.
# Run the tests during OpenResty's initialization instead, without opening a listener.
& docker run --rm --mount $gatewayMount --entrypoint /usr/local/openresty/bin/openresty $gatewayImage -c /etc/evp-gateway/test-runner.conf
if ($LASTEXITCODE -ne 0) { throw 'Gateway Lua tests failed. No running container was changed.' }
& docker run --rm --mount $gatewayMount --entrypoint /usr/local/openresty/bin/openresty $gatewayImage -t -c /etc/evp-gateway/nginx.conf
if ($LASTEXITCODE -ne 0) { throw 'Gateway configuration check failed. No running container was changed.' }
Write-Host 'Gateway tests and configuration check passed. Existing Keycloak was not changed.'
