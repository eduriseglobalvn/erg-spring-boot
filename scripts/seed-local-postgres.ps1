param(
    [string]$EnvFile = "ops/docker/.env.dev",
    [string]$SqlFile = "load/fixtures/seed-postgres-scale.sql",
    [string]$Container = "erg-dev-postgres-1"
)

$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $repoRoot

if (-not (Test-Path -LiteralPath $EnvFile)) { throw "Env file not found: $EnvFile" }
if (-not (Test-Path -LiteralPath $SqlFile)) { throw "SQL fixture not found: $SqlFile" }

$db = "erg"
$user = "erg"
$password = "erg-local-password"
Get-Content -LiteralPath $EnvFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=\s]*)\s*=\s*(.*)\s*$') {
        $name = $matches[1]
        $value = $matches[2].Trim().Trim('"').Trim("'")
        if ($name -eq 'POSTGRES_DB') { $db = $value }
        if ($name -eq 'POSTGRES_USER') { $user = $value }
        if ($name -eq 'POSTGRES_PASSWORD') { $password = $value }
    }
}

Write-Host "Seeding local PostgreSQL container $Container database $db (idempotent; no DROP/TRUNCATE)."
$env:PGPASSWORD = $password
Get-Content -LiteralPath $SqlFile -Raw | docker exec -i $Container psql -v ON_ERROR_STOP=1 -U $user -d $db
if ($LASTEXITCODE -ne 0) { throw "Local PostgreSQL seed failed." }
Write-Host "Local PostgreSQL seed completed."
