param(
    [switch]$Execute
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$composeDir = (Resolve-Path (Join-Path $root "ops\docker")).Path
$envFile = Join-Path $composeDir ".env.dev"
$baseCompose = Join-Path $composeDir "compose.yml"
$devCompose = Join-Path $composeDir "compose.dev.yml"
$oldContainerName = "erg-dev-redis-1"
$oldVolumeName = "erg-dev_redis-data"
$postgresVolumeName = "erg-dev_postgres-data"

if (-not $composeDir.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Resolved Compose directory escaped workspace: $composeDir"
}
if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw "Missing local environment file: $envFile"
}

function Invoke-Docker([string[]]$Arguments) {
    $output = & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
    return $output
}

function Compose-Arguments([string[]]$Tail) {
    return @(
        "compose",
        "--project-name", "erg-dev",
        "--project-directory", $composeDir,
        "--env-file", $envFile,
        "-f", $baseCompose,
        "-f", $devCompose
    ) + $Tail
}

function Exact-Resource([string]$Kind, [string]$Name) {
    $format = if ($Kind -eq "container") { "{{.Names}}" } else { "{{.Name}}" }
    $args = if ($Kind -eq "container") {
        @("ps", "-a", "--filter", "name=^/$Name$", "--format", $format)
    } else {
        @("volume", "ls", "--filter", "name=^$Name$", "--format", $format)
    }
    return @(& docker @args) | Where-Object { $_ -eq $Name }
}

function Wait-Healthy([string]$ContainerName) {
    $deadline = [DateTimeOffset]::UtcNow.AddMinutes(2)
    do {
        $status = & docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" $ContainerName 2>$null
        if ($LASTEXITCODE -eq 0 -and $status -eq "healthy") { return }
        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    throw "Container did not become healthy: $ContainerName (last status: $status)"
}

$oldContainer = Exact-Resource "container" $oldContainerName
$oldVolume = Exact-Resource "volume" $oldVolumeName
$postgresVolumeBefore = Exact-Resource "volume" $postgresVolumeName

Write-Host "Workspace: $root"
Write-Host "Compose project: erg-dev"
Write-Host "Old Redis container: $($oldContainer -join ', ')"
Write-Host "Old Redis volume: $($oldVolume -join ', ')"
Write-Host "PostgreSQL volume preserved: $($postgresVolumeBefore -join ', ')"

if (-not $Execute) {
    Write-Host "Dry run only. Re-run with -Execute to remove only the exact old Redis container/volume."
    exit 0
}

if ($oldContainer) {
    Invoke-Docker @("rm", "--force", $oldContainerName) | Out-Null
}
if ($oldVolume) {
    Invoke-Docker @("volume", "rm", $oldVolumeName) | Out-Null
}

Invoke-Docker (Compose-Arguments @("pull", "redis-state", "redis-cache")) | Out-Host
Invoke-Docker (Compose-Arguments @("up", "-d", "--remove-orphans", "redis-state", "redis-cache")) | Out-Host

Wait-Healthy "erg-dev-redis-state-1"
Wait-Healthy "erg-dev-redis-cache-1"

$stateVersion = Invoke-Docker @("exec", "erg-dev-redis-state-1", "redis-cli", "--raw", "INFO", "server")
$cacheVersion = Invoke-Docker @("exec", "erg-dev-redis-cache-1", "redis-cli", "--raw", "INFO", "server")
$increx = Invoke-Docker @("exec", "erg-dev-redis-state-1", "redis-cli", "--raw", "COMMAND", "INFO", "INCREX")
$statePolicy = Invoke-Docker @("exec", "erg-dev-redis-state-1", "redis-cli", "--raw", "CONFIG", "GET", "maxmemory-policy")
$cachePolicy = Invoke-Docker @("exec", "erg-dev-redis-cache-1", "redis-cli", "--raw", "CONFIG", "GET", "maxmemory-policy")
$stateAppendOnly = Invoke-Docker @("exec", "erg-dev-redis-state-1", "redis-cli", "--raw", "CONFIG", "GET", "appendonly")
$cacheAppendOnly = Invoke-Docker @("exec", "erg-dev-redis-cache-1", "redis-cli", "--raw", "CONFIG", "GET", "appendonly")

if (($stateVersion -join "`n") -notmatch "redis_version:8\.8\.0") { throw "redis-state is not Redis 8.8.0" }
if (($cacheVersion -join "`n") -notmatch "redis_version:8\.8\.0") { throw "redis-cache is not Redis 8.8.0" }
if (-not $increx) { throw "Redis 8.8 INCREX capability was not found" }
if (($statePolicy -join "`n") -notmatch "noeviction") { throw "redis-state policy is not noeviction" }
if (($cachePolicy -join "`n") -notmatch "allkeys-lfu") { throw "redis-cache policy is not allkeys-lfu" }
if (($stateAppendOnly -join "`n") -notmatch "yes") { throw "redis-state AOF is not enabled" }
if (($cacheAppendOnly -join "`n") -notmatch "no") { throw "redis-cache AOF is not disabled" }

$postgresVolumeAfter = Exact-Resource "volume" $postgresVolumeName
if (($postgresVolumeBefore -join "") -ne ($postgresVolumeAfter -join "")) {
    throw "PostgreSQL volume changed during Redis migration"
}

Write-Host "Redis 8.8 local migration passed: state and cache are healthy; PostgreSQL volume is unchanged."

