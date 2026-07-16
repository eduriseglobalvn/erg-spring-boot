param(
    [Parameter(Position = 0)]
    [ValidateSet("dev", "build", "push", "up", "deploy", "down", "logs", "config", "redis-migrate", "help")]
    [string]$Action = "help",

    [Parameter(Position = 1)]
    [ValidateRange(1, 32)]
    [int]$Replicas = 2,

    [string]$Image = $env:ERG_APP_IMAGE,
    [string]$EnvFile
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$dockerDir = Join-Path $root "ops\docker"
$baseCompose = Join-Path $dockerDir "compose.yml"
$devCompose = Join-Path $dockerDir "compose.dev.yml"
$nativeCompose = Join-Path $dockerDir "compose.native.yml"
$devEnv = if ($EnvFile) { $EnvFile } else { Join-Path $dockerDir ".env.dev" }
$productionEnv = if ($EnvFile) { $EnvFile } else { Join-Path $dockerDir ".env.production" }

function Invoke-Docker([string[]]$Arguments) {
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Ensure-LocalEnv([string]$Path, [string]$Example) {
    if (-not (Test-Path -LiteralPath $Path)) {
        Copy-Item -LiteralPath (Join-Path $dockerDir $Example) -Destination $Path
        Write-Host "Created local environment file: $Path"
    }
}

function Compose-Args([string]$EnvironmentFile, [string]$Overlay) {
    return @(
        "compose",
        "--project-directory", $dockerDir,
        "--env-file", $EnvironmentFile,
        "-f", $baseCompose,
        "-f", $Overlay
    )
}

switch ($Action) {
    "dev" {
        Ensure-LocalEnv $devEnv ".env.dev.example"
        Invoke-Docker ((Compose-Args $devEnv $devCompose) + @("up", "-d", "--remove-orphans"))
        Write-Host "Local infrastructure is available through Nginx at http://localhost:8080"
        Write-Host "Run Spring Boot from the IDE on port 1606 using $devEnv"
    }
    "build" {
        if (-not $Image) { $Image = "erg-spring:local-native" }
        Invoke-Docker @(
            "buildx", "build",
            "--platform", "linux/amd64",
            "--load",
            "--tag", $Image,
            $root
        )
    }
    "push" {
        if (-not $Image) { throw "Set -Image to a Registry tag, for example ghcr.io/acme/erg-spring:abc123" }
        Invoke-Docker @("push", $Image)
    }
    "up" {
        Ensure-LocalEnv $productionEnv ".env.production.example"
        if (-not $Image) { $Image = "erg-spring:local-native" }
        $env:ERG_APP_IMAGE = $Image
        Invoke-Docker ((Compose-Args $productionEnv $nativeCompose) + @(
            "up", "-d", "--remove-orphans", "--scale", "backend=$Replicas"
        ))
    }
    "deploy" {
        if (-not (Test-Path -LiteralPath $productionEnv)) {
            throw "Missing production env file: $productionEnv"
        }
        if ($Image) { $env:ERG_APP_IMAGE = $Image }
        $compose = Compose-Args $productionEnv $nativeCompose
        Invoke-Docker ($compose + @("pull"))
        Invoke-Docker ($compose + @(
            "up", "-d", "--no-build", "--remove-orphans", "--scale", "backend=$Replicas"
        ))
    }
    "down" {
        if (Test-Path -LiteralPath $devEnv) {
            Invoke-Docker ((Compose-Args $devEnv $devCompose) + @("down", "--remove-orphans"))
        }
        if (Test-Path -LiteralPath $productionEnv) {
            Invoke-Docker ((Compose-Args $productionEnv $nativeCompose) + @("down", "--remove-orphans"))
        }
    }
    "logs" {
        Ensure-LocalEnv $productionEnv ".env.production.example"
        if (-not $Image) { $env:ERG_APP_IMAGE = "erg-spring:local-native" }
        Invoke-Docker ((Compose-Args $productionEnv $nativeCompose) + @("logs", "-f", "nginx", "backend"))
    }
    "config" {
        Ensure-LocalEnv $devEnv ".env.dev.example"
        Ensure-LocalEnv $productionEnv ".env.production.example"
        if (-not $Image) { $env:ERG_APP_IMAGE = "erg-spring:local-native" }
        Invoke-Docker ((Compose-Args $devEnv $devCompose) + @("config", "--quiet"))
        Invoke-Docker ((Compose-Args $productionEnv $nativeCompose) + @("config", "--quiet"))
        Write-Host "Both Compose configurations are valid."
    }
    "redis-migrate" {
        & (Join-Path $PSScriptRoot "migrate-redis-8.8-local.ps1") -Execute
        if ($LASTEXITCODE -ne 0) { throw "Redis migration failed with exit code $LASTEXITCODE" }
    }
    default {
        Write-Host @"
Usage: .\scripts\docker.ps1 <action> [replicas] [-Image registry/name:tag] [-EnvFile path]

  dev       Start PostgreSQL, Redis and Nginx for the IDE-run backend.
  build     Build a Linux amd64 native image locally (default erg-spring:local-native).
  push      Push the image supplied with -Image.
  up        Run the native image locally behind Nginx with N backend replicas.
  deploy    Pull and run a prebuilt image on the server. Never builds.
  down      Stop both local development and native stacks.
  logs      Follow Nginx and backend logs.
  config    Validate development and native Compose configurations.
  redis-migrate  Destructively replace only the local Redis 7.4 state with Redis 8.8 state/cache services.
"@
    }
}
