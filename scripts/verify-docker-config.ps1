$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$failures = [System.Collections.Generic.List[string]]::new()

function Require-File([string]$RelativePath) {
    $path = Join-Path $root $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        $failures.Add("Missing file: $RelativePath")
        return $null
    }
    return Get-Content -LiteralPath $path -Raw
}

function Require-Match([string]$Content, [string]$Pattern, [string]$Message) {
    if ($null -eq $Content -or $Content -notmatch $Pattern) {
        $failures.Add($Message)
    }
}

$dockerfile = Require-File "Dockerfile"
$composeBase = Require-File "ops/docker/compose.yml"
$composeDev = Require-File "ops/docker/compose.dev.yml"
$composeNative = Require-File "ops/docker/compose.native.yml"
$redisState = Require-File "ops/docker/redis/state.conf"
$redisCache = Require-File "ops/docker/redis/cache.conf"
$redisAcl = Require-File "ops/docker/redis/users.acl.example"
$redisVerify = Require-File "ops/docker/redis/verify-redis.sh"
$nginxDev = Require-File "ops/docker/nginx/dev.conf"
$nginxNative = Require-File "ops/docker/nginx/native.conf"
$envDev = Require-File "ops/docker/.env.dev.example"
$envProduction = Require-File "ops/docker/.env.production.example"
$windowsScript = Require-File "scripts/docker.ps1"
$linuxScript = Require-File "scripts/docker.sh"

Require-Match $dockerfile 'ghcr\.io/graalvm/native-image-community:25' "Dockerfile must use GraalVM Native Image 25"
Require-Match $dockerfile 'native:compile' "Dockerfile must compile a native executable"
Require-Match $dockerfile 'USER\s+erg' "Runtime image must run as the non-root erg user"
Require-Match $dockerfile 'SPRING_PROFILES_ACTIVE=prod' "Native AOT build must use the production Spring profile"
Require-Match $dockerfile 'FLYWAY_ENABLED=true' "Native AOT build must include the production Flyway bean"
Require-Match $dockerfile 'target/classes/db/migration/postgres' "Runtime image must include Flyway SQL files"
Require-Match $dockerfile '/ready' "Runtime healthcheck must use the public readiness endpoint"
Require-Match $composeBase 'postgres:17' "Base Compose must provide PostgreSQL 17"
Require-Match $composeBase '(?m)^\s{2}redis-state:' "Base Compose must define redis-state"
Require-Match $composeBase '(?m)^\s{2}redis-cache:' "Base Compose must define redis-cache"
Require-Match $composeBase 'redis:8\.8\.0-alpine3\.23' "Redis services must pin Redis 8.8.0 on Alpine 3.23"
Require-Match $composeBase 'state\.conf' "redis-state must load state.conf"
Require-Match $composeBase 'cache\.conf' "redis-cache must load cache.conf"
Require-Match $composeBase 'redis-state-data:/data' "redis-state must use its own volume"
Require-Match $composeBase 'redis-cache-data:/data' "redis-cache must use its own volume"
Require-Match $redisState '(?m)^maxmemory\s+768mb$' "redis-state must reserve 768 MiB on the 16 GiB profile"
Require-Match $redisState '(?m)^maxmemory-policy\s+noeviction$' "redis-state must use noeviction"
Require-Match $redisState '(?m)^appendonly\s+yes$' "redis-state must enable AOF"
Require-Match $redisState '(?m)^appendfsync\s+everysec$' "redis-state must fsync AOF every second"
Require-Match $redisCache '(?m)^maxmemory\s+2gb$' "redis-cache must reserve 2 GiB on the 16 GiB profile"
Require-Match $redisCache '(?m)^maxmemory-policy\s+allkeys-lfu$' "redis-cache must use allkeys-lfu"
Require-Match $redisCache '(?m)^appendonly\s+no$' "redis-cache must disable AOF"
Require-Match $redisCache '(?m)^save\s+""$' "redis-cache must disable RDB snapshots"
Require-Match $redisAcl '(?m)^user default off$' "Redis ACL example must disable the default user"
Require-Match $redisAcl '\+increx' "State ACL must permit Redis 8.8 INCREX"
Require-Match $redisAcl '&erg:state:\*' "State ACL must restrict Pub/Sub channels"
if ($null -ne $redisAcl -and $redisAcl -match '(?m)^user\s+.*\+@all(?:\s|$)') {
    $failures.Add("Redis ACL must not grant +@all")
}
Require-Match $redisVerify 'expect_denied' "Redis verification must assert denied commands"
Require-Match $redisVerify 'FLUSHALL' "Redis verification must deny destructive database commands"
Require-Match $composeDev '127\.0\.0\.1:\$\{REDIS_STATE_HOST_PORT:-6379\}:6379' "Development state Redis must bind loopback"
Require-Match $composeDev '127\.0\.0\.1:\$\{REDIS_CACHE_HOST_PORT:-6380\}:6379' "Development cache Redis must bind loopback"
Require-Match $composeDev 'host\.docker\.internal' "Development Compose must support Windows host.docker.internal"
Require-Match $composeNative 'image:\s*\$\{ERG_APP_IMAGE' "Native Compose must use ERG_APP_IMAGE"
Require-Match $composeNative '/ready' "Native backend healthcheck must use the public readiness endpoint"
if ($null -ne $composeNative -and $composeNative -match '(?m)^\s+build\s*:') {
    $failures.Add("Native/server Compose must not contain a build directive")
}
Require-Match $nginxDev 'host\.docker\.internal:1606' "Development Nginx must proxy to IDE port 1606"
Require-Match $nginxNative 'resolver\s+127\.0\.0\.11' "Native Nginx must use Docker DNS"
Require-Match $nginxNative 'resolve' "Native Nginx upstream must be re-resolved"

foreach ($name in @(
    "SPRING_DATASOURCE_URL",
    "SPRING_DATASOURCE_USERNAME",
    "SPRING_DATASOURCE_PASSWORD",
    "SPRING_DATA_REDIS_URL",
    "SPRING_PROFILES_ACTIVE",
    "SERVER_PORT",
    "FLYWAY_ENABLED",
    "SECRET_AUTH__JWT_SECRET",
    "SECRET_AUTH__JWT_REFRESH_SECRET"
)) {
    Require-Match $envDev "(?m)^$([regex]::Escape($name))=" "Development env must define $name"
    Require-Match $envProduction "(?m)^$([regex]::Escape($name))=" "Production env must define $name"
}

Require-Match $envProduction '(?m)^ERG_APP_IMAGE=.+:.+' "Production env must define a versioned ERG_APP_IMAGE"
Require-Match $envProduction '(?m)^FLYWAY_LOCATIONS=filesystem:/app/db/migration/postgres$' "Native production env must use filesystem Flyway migrations"
Require-Match $windowsScript 'linux/amd64' "Windows build command must target linux/amd64"
Require-Match $windowsScript '(?m)\bpull\b' "Windows deploy command must pull prebuilt images"
Require-Match $linuxScript '(?m)\bpull\b' "Linux deploy command must pull prebuilt images"

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ -ErrorAction Continue }
    Write-Host "Docker configuration contract failed with $($failures.Count) issue(s)." -ForegroundColor Red
    exit 1
}

Write-Host "Docker configuration contract passed." -ForegroundColor Green
