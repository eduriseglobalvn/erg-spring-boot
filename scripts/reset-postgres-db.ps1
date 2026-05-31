param(
    [string]$BackupDir = "backups",
    [string]$DatabaseUrl = $env:SPRING_DATASOURCE_URL,
    [string]$Username = $env:SPRING_DATASOURCE_USERNAME,
    [string]$Password = $env:SPRING_DATASOURCE_PASSWORD,
    [string]$Confirmation = $env:CONFIRM_RESET_POSTGRES_DB
)

$ErrorActionPreference = "Stop"

if ($Confirmation -ne "I_UNDERSTAND_THIS_DELETES_DATA") {
    throw "Refusing to reset PostgreSQL DB. Set CONFIRM_RESET_POSTGRES_DB=I_UNDERSTAND_THIS_DELETES_DATA."
}

if ([string]::IsNullOrWhiteSpace($DatabaseUrl)) {
    throw "SPRING_DATASOURCE_URL is required."
}

if (-not (Get-Command pg_dump -ErrorAction SilentlyContinue)) {
    throw "pg_dump was not found on PATH."
}

if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    throw "psql was not found on PATH."
}

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupPath = Join-Path $BackupDir "postgres-$timestamp.dump"

if (-not [string]::IsNullOrWhiteSpace($Password)) {
    $env:PGPASSWORD = $Password
}

Write-Host "Creating backup: $backupPath"
& pg_dump --format=custom --file=$backupPath --username=$Username $DatabaseUrl
if ($LASTEXITCODE -ne 0) {
    throw "pg_dump failed. PostgreSQL DB was not reset."
}

Write-Host "Dropping and recreating public schema..."
& psql --username=$Username $DatabaseUrl --command="DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;"
if ($LASTEXITCODE -ne 0) {
    throw "psql reset command failed. Backup is available at $backupPath."
}

Write-Host "PostgreSQL DB reset complete. Backup: $backupPath"
Write-Host "Start the application or run the migration task next so Flyway recreates the schema."
