param(
    [Parameter(Position = 0)]
    [ValidateSet("dev", "test", "build", "release", "push", "up", "prod", "deploy", "down", "logs", "config", "redis-migrate", "help")]
    [string]$Action = "help",

    [Parameter(Position = 1)]
    [ValidateRange(1, 32)]
    [int]$Replicas = 2,

    [string]$Image = $env:ERG_APP_IMAGE,
    [string]$RollbackImage = $env:ERG_ROLLBACK_IMAGE,
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

function Assert-ImmutableImage([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "Set -Image to a registry image with a Git SHA tag, for example ghcr.io/acme/erg-spring:abc1234"
    }
    if ($Value -notmatch '^[^/]+/.+:[0-9a-f]{7,64}$') {
        throw "Image must use a registry/repository:git-sha tag (7-64 lowercase hex characters); floating tags such as latest, main or dev are not allowed"
    }
}

function Assert-ProductionImage([string]$Value, [string]$Name = "Image") {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "$Name must be a registry image pinned by digest, for example ghcr.io/acme/erg-spring@sha256:<64-hex-digest>"
    }
    if ($Value -notmatch '^[^/]+/.+@sha256:[0-9a-f]{64}$') {
        throw "$Name must use a registry/repository@sha256:<64 lowercase hex digest>; Git-SHA tags and floating tags are not allowed for production deploy"
    }
}

function Read-ConfiguredValue([string]$Path, [string]$Name) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match "^$([regex]::Escape($Name))=(.*)$") {
            return $Matches[1].Trim()
        }
    }
    return $null
}

function Invoke-ProductionDeployment([string]$EnvironmentFile) {
    $configuredImage = Read-ConfiguredValue $EnvironmentFile "ERG_APP_IMAGE"
    $targetImage = if ($Image) { $Image } else { $configuredImage }
    $rollbackImage = if ($RollbackImage) { $RollbackImage } else {
        Read-ConfiguredValue $EnvironmentFile "ERG_ROLLBACK_IMAGE"
    }
    Assert-ProductionImage $targetImage "Production image"
    Assert-ProductionImage $rollbackImage "Rollback image"
    if ($targetImage -eq $rollbackImage) {
        throw "Production image and rollback image must be different digests"
    }

    $compose = Compose-Args $EnvironmentFile $nativeCompose
    $originalImage = $env:ERG_APP_IMAGE
    try {
        $env:ERG_APP_IMAGE = $targetImage
        Invoke-Docker ($compose + @("pull"))
        Invoke-Docker ($compose + @(
            "up", "-d", "--no-build", "--remove-orphans", "--scale", "backend=$Replicas",
            "--wait", "--wait-timeout", "180"
        ))
        Write-Host "Production image is healthy: $targetImage"
    } catch {
        Write-Warning "Production health gate failed for $targetImage. Rolling back to $rollbackImage."
        try {
            $env:ERG_APP_IMAGE = $rollbackImage
            Invoke-Docker ($compose + @("pull"))
            Invoke-Docker ($compose + @(
                "up", "-d", "--no-build", "--remove-orphans", "--scale", "backend=$Replicas",
                "--wait", "--wait-timeout", "180"
            ))
            Write-Warning "Rollback completed: $rollbackImage"
        } catch {
            throw "Production deploy failed and rollback also failed. Target: $targetImage; rollback: $rollbackImage; error: $($_.Exception.Message)"
        }
        throw "Production deploy failed health gate; rollback completed to $rollbackImage"
    } finally {
        if ($null -eq $originalImage) {
            Remove-Item Env:ERG_APP_IMAGE -ErrorAction SilentlyContinue
        } else {
            $env:ERG_APP_IMAGE = $originalImage
        }
    }
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
    "release" {
        Assert-ImmutableImage $Image
        Invoke-Docker @(
            "buildx", "build",
            "--platform", "linux/amd64",
            "--push",
            "--tag", $Image,
            $root
        )
        Write-Host "Published immutable native image $Image"
    }
    "push" {
        Assert-ImmutableImage $Image
        Invoke-Docker @("push", $Image)
    }
    "test" {
        Ensure-LocalEnv $productionEnv ".env.production.example"
        if (-not $Image) { $Image = "erg-spring:local-native" }
        $env:ERG_APP_IMAGE = $Image
        Invoke-Docker ((Compose-Args $productionEnv $nativeCompose) + @(
            "up", "-d", "--remove-orphans", "--scale", "backend=$Replicas"
        ))
    }
    "up" {
        Ensure-LocalEnv $productionEnv ".env.production.example"
        if (-not $Image) { $Image = "erg-spring:local-native" }
        $env:ERG_APP_IMAGE = $Image
        Invoke-Docker ((Compose-Args $productionEnv $nativeCompose) + @(
            "up", "-d", "--remove-orphans", "--scale", "backend=$Replicas"
        ))
    }
    "prod" {
        if (-not (Test-Path -LiteralPath $productionEnv)) {
            throw "Missing production env file: $productionEnv"
        }
        Invoke-ProductionDeployment $productionEnv
    }
    "deploy" {
        if (-not (Test-Path -LiteralPath $productionEnv)) {
            throw "Missing production env file: $productionEnv"
        }
        Invoke-ProductionDeployment $productionEnv
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
Usage: .\scripts\docker.ps1 <action> [replicas] [-Image registry/name:tag] [-RollbackImage registry/name@sha256:digest] [-EnvFile path]

  dev       Start PostgreSQL, Redis and Nginx for the IDE-run backend.
  build     Build a Linux amd64 native image locally (default erg-spring:local-native).
  release   Build and push one immutable Linux amd64 image in one command.
  push      Push an existing immutable Git-SHA image supplied with -Image.
  test      Run the already-built native image locally behind Nginx with N backend replicas.
  up        Alias of test.
  prod      Pull and run a digest-pinned image with health gate and digest rollback.
  deploy    Alias of prod.
  down      Stop both local development and native stacks.
  logs      Follow Nginx and backend logs.
  config    Validate development and native Compose configurations.
  redis-migrate  Destructively replace only the local Redis 7.4 state with Redis 8.8 state/cache services.
"@
    }
}
