param(
    [string]$OutputDirectory = "load/results/evidence",
    [string]$ComposeEnv = "ops/docker/.env.dev"
)
$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "../../..")
$output = Join-Path $root $OutputDirectory
New-Item -ItemType Directory -Force -Path $output | Out-Null

function Capture([string]$Name, [scriptblock]$Command) {
    & $Command 2>&1 | Out-File -Encoding utf8 (Join-Path $output $Name)
}

Capture "revision.txt" {
    git -C $root rev-parse HEAD
    $dirty = (git -C $root status --porcelain) -join "`n"
    $bytes = [Text.Encoding]::UTF8.GetBytes($dirty)
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $hash = $sha.ComputeHash($bytes)
        "dirty-sha256=" + ([BitConverter]::ToString($hash) -replace '-', '').ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}
Capture "compose-images.txt" { docker compose --env-file (Join-Path $root $ComposeEnv) -f (Join-Path $root "ops/docker/compose.yml") -f (Join-Path $root "ops/docker/compose.dev.yml") images --format json }
Capture "compose-ps.txt" { docker compose --env-file (Join-Path $root $ComposeEnv) -f (Join-Path $root "ops/docker/compose.yml") -f (Join-Path $root "ops/docker/compose.dev.yml") ps --format json }
$compose = @('--env-file', (Join-Path $root $ComposeEnv), '-f', (Join-Path $root 'ops/docker/compose.yml'), '-f', (Join-Path $root 'ops/docker/compose.dev.yml'))
$stateId = docker compose @compose ps -q redis-state
$cacheId = docker compose @compose ps -q redis-cache
Capture "redis-state-info.txt" { docker exec $stateId redis-cli --no-auth-warning INFO server memory persistence stats }
Capture "redis-cache-info.txt" { docker exec $cacheId redis-cli --no-auth-warning INFO server memory persistence stats }
Capture "redis-state-config.txt" { docker exec $stateId redis-cli --no-auth-warning CONFIG GET maxmemory maxmemory-policy appendonly appendfsync }
Capture "redis-cache-config.txt" { docker exec $cacheId redis-cli --no-auth-warning CONFIG GET maxmemory maxmemory-policy appendonly save }
@{ environment = "local-docker"; startedAt = (Get-Date).ToUniversalTime().ToString("o") } |
    ConvertTo-Json | Out-File -Encoding utf8 (Join-Path $output "manifest.json")
Write-Host "Evidence captured at $output (no URLs, tokens, cookies, passwords, or ACL material)."
