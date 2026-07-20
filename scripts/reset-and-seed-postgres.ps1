param(
    [string]$EnvFile = ".env",
    [string]$Confirmation = $env:CONFIRM_RESET_POSTGRES_DB,
    [string]$FlywayVersion = "11.14.1",
    [switch]$SkipSeed,
    [switch]$SkipVerify
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $repoRoot

function Import-DotEnvFile {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        throw "Env file not found: $Path"
    }

    Get-Content $Path | ForEach-Object {
        if ($_ -match "^\s*#" -or $_ -match "^\s*$") {
            return
        }

        if ($_ -match "^\s*([^#][^=\s]*)\s*=\s*(.*)\s*$") {
            $name = $matches[1]
            $value = $matches[2].Trim()

            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }

            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

function Require-Env {
    param([string]$Name)

    $value = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "$Name is required."
    }
    return $value
}

function Describe-JdbcTarget {
    param([string]$JdbcUrl)

    if ($JdbcUrl -match "^jdbc:postgresql://([^/:?]+)(?::([0-9]+))?/([^?]+)") {
        $hostName = $matches[1]
        $port = if ($matches[2]) { $matches[2] } else { "5432" }
        $database = $matches[3]
        return "$hostName`:$port/$database"
    }

    return "<unparsed jdbc url>"
}

function Invoke-Maven {
    param([string[]]$Arguments)

    & ".\mvnw.cmd" @Arguments
    if ($LASTEXITCODE -ne 0) {
        $safeArguments = $Arguments | ForEach-Object {
            if ($_ -like "-Dflyway.password=*") {
                "-Dflyway.password=<redacted>"
            } else {
                $_
            }
        }
        throw "Maven command failed: $($safeArguments -join ' ')"
    }
}

if ($Confirmation -ne "I_UNDERSTAND_THIS_DELETES_DATA") {
    throw "Refusing to reset PostgreSQL DB. Pass -Confirmation I_UNDERSTAND_THIS_DELETES_DATA or set CONFIRM_RESET_POSTGRES_DB=I_UNDERSTAND_THIS_DELETES_DATA."
}

Import-DotEnvFile -Path $EnvFile

$databaseUrl = Require-Env "SPRING_DATASOURCE_URL"
$databaseUser = Require-Env "SPRING_DATASOURCE_USERNAME"
$databasePassword = [Environment]::GetEnvironmentVariable("SPRING_DATASOURCE_PASSWORD", "Process")

Write-Host "Target PostgreSQL schema: $(Describe-JdbcTarget $databaseUrl) / schema public"
Write-Host "Step 1/4: Flyway clean will drop every object in schema public."

Invoke-Maven @(
    "-q",
    "org.flywaydb:flyway-maven-plugin:$($FlywayVersion):clean",
    "-Dflyway.url=$databaseUrl",
    "-Dflyway.user=$databaseUser",
    "-Dflyway.password=$databasePassword",
    "-Dflyway.locations=filesystem:src/main/resources/db/migration/postgres",
    "-Dflyway.schemas=public",
    "-Dflyway.cleanDisabled=false"
)

Write-Host "Step 2/4: Flyway migrate will recreate schema from V1..latest."

Invoke-Maven @(
    "-q",
    "org.flywaydb:flyway-maven-plugin:$($FlywayVersion):migrate",
    "-Dflyway.url=$databaseUrl",
    "-Dflyway.user=$databaseUser",
    "-Dflyway.password=$databasePassword",
    "-Dflyway.locations=filesystem:src/main/resources/db/migration/postgres",
    "-Dflyway.schemas=public",
    "-Dflyway.baselineOnMigrate=true",
    "-Dflyway.baselineVersion=0",
    "-Dflyway.table=flyway_schema_history"
)

if (-not $SkipSeed) {
    Write-Host "Step 3/4: Canonical curriculum/content seed is applied by Flyway V41."
} else {
    Write-Host "Step 3/4: Additional seed step skipped by -SkipSeed. Flyway V41 canonical seed still ran during migration."
}

if (-not $SkipVerify) {
    Write-Host "Step 4/4: Verify canonical learning-resource migration and API surface contracts."

    Invoke-Maven @(
        "-Dtest=CanonicalLearningResourceMigrationContractTest,LearningResourceLegacyApiRemovalTest,LearningResourceLibraryReadServiceTest,GraphQlEndpointConfigurationTest",
        "test"
    )
} else {
    Write-Host "Step 4/4: Verification skipped by -SkipVerify."
}

Write-Host "Reset, migrate, seed, and verification completed."
