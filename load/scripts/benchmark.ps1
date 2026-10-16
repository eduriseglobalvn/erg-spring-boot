param(
    [ValidateSet("All", "Infrastructure", "jvm", "native")]
    [string]$Runtime = "All",
    [switch]$DryRun,
    [switch]$FullLoad,
    [switch]$KeepInfrastructure
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "benchmark-lib.ps1")
Set-Location $script:RepoRoot

$run = New-BenchmarkRunDirectory
$manifestPath = Join-Path $run.Path "manifest.json"
$manifest = [ordered]@{
    runId = $run.Id
    startedAt = (Get-Date).ToUniversalTime().ToString("o")
    mode = $(if ($FullLoad) { "full" } else { "smoke" })
    runtimes = [ordered]@{}
    cleanupVerified = $false
}

function Save-Manifest {
    $manifest | ConvertTo-Json -Depth 20 | Set-Content -Encoding utf8 $manifestPath
}

function Invoke-Seed {
    $sql = Get-Content -Raw (Join-Path $script:RepoRoot "load/fixtures/seed-postgres-scale.sql")
    $sql | & docker compose @(Get-BenchmarkComposeArgs) exec -T postgres psql -v ON_ERROR_STOP=1 -U erg -d erg_benchmark
    if ($LASTEXITCODE -ne 0) { throw "Benchmark fixture seed failed" }
}

function Start-Infrastructure {
    Invoke-BenchmarkCompose -Arguments @(
        "up", "-d", "postgres", "redis-state", "redis-cache", "prometheus", "grafana", "cadvisor",
        "postgres-exporter", "redis-state-exporter", "redis-cache-exporter"
    )
    foreach ($service in @("postgres", "redis-state", "redis-cache")) {
        Wait-BenchmarkServiceHealthy $service
    }
    Wait-BenchmarkObservabilityReady
}

function Invoke-K6Scenario(
    [string]$RuntimeName,
    [string]$ScriptName,
    [string]$Label,
    [string[]]$Environment
) {
    $baseName = [IO.Path]::GetFileNameWithoutExtension($ScriptName)
    $relativeResult = "$($run.Id)/$RuntimeName/k6/$baseName-$Label.json"
    $environmentArgs = @()
    foreach ($value in $Environment) {
        $environmentArgs += @("-e", $value)
    }
    $arguments = @(
        "--profile", "load", "run", "--rm"
    ) + $environmentArgs + @(
        "k6", "run", "--summary-trend-stats=avg,min,med,max,p(50),p(90),p(95),p(99)",
        "--summary-export=/results/$relativeResult", "--out", "experimental-prometheus-rw",
        "/load/k6/$ScriptName"
    )
    Invoke-BenchmarkCompose -Arguments $arguments
}

function Capture-Metrics([string]$RuntimeName, [string]$Label) {
    $output = Join-Path $run.Path "$RuntimeName/metrics"
    & docker stats --no-stream --format "{{json .}}" | Set-Content -Encoding utf8 (Join-Path $output "docker-stats-$Label.ndjson")
    & docker compose @(Get-BenchmarkComposeArgs) exec -T postgres psql -U erg -d erg_benchmark -Atc "select json_build_object('connections',count(*)) from pg_stat_activity" |
        Set-Content -Encoding utf8 (Join-Path $output "postgres-$Label.json")
    Write-BenchmarkObservabilityEvidence $output $Label $RuntimeName
}

function Invoke-RuntimeRun([ValidateSet("jvm", "native")][string]$RuntimeName) {
    $service = Get-BenchmarkRuntimeService $RuntimeName
    $started = Get-Date
    Invoke-BenchmarkCompose -Arguments @("--profile", $RuntimeName, "up", "-d", $service)
    Wait-BenchmarkServiceHealthy $service 600
    $startupSeconds = ((Get-Date) - $started).TotalSeconds
    Wait-BenchmarkObservabilityReady -RequireBackend -Runtime $RuntimeName
    Write-BenchmarkImageMetadata $RuntimeName (Join-Path $run.Path "$RuntimeName/image.json")
    Invoke-Seed
    Invoke-K6Scenario $RuntimeName "erg-5k-steady.js" "smoke" @("SMOKE=true")
    Invoke-K6Scenario $RuntimeName "erg-graphql-critical.js" "smoke" @("SMOKE=true")
    $receipts = @("rest-smoke", "graphql-smoke")
    if ($FullLoad) {
        Invoke-K6Scenario $RuntimeName "erg-5k-steady.js" "warmup" @(
            "WARMUP=true", "TARGET_VUS=100", "STEP_DURATION=5m"
        )
        Invoke-K6Scenario $RuntimeName "erg-graphql-critical.js" "warmup" @(
            "WARMUP=true", "TARGET_VUS=100", "STEP_DURATION=5m"
        )
        foreach ($step in Get-BenchmarkLoadSteps) {
            Assert-BenchmarkCapacity
            $label = "vus-$($step.TargetVus)"
            $stepEnvironment = @(
                "SMOKE=false",
                "TARGET_VUS=$($step.TargetVus)",
                "STEP_DURATION=$($step.Duration)"
            )
            Invoke-BenchmarkMeasuredStep `
                -Operation { Invoke-K6Scenario $RuntimeName "erg-5k-steady.js" $label $stepEnvironment } `
                -EvidenceCapture { Capture-Metrics $RuntimeName "rest-$label" }
            Invoke-BenchmarkMeasuredStep `
                -Operation { Invoke-K6Scenario $RuntimeName "erg-graphql-critical.js" $label $stepEnvironment } `
                -EvidenceCapture { Capture-Metrics $RuntimeName "graphql-$label" }
            $receipts += @("rest-$label", "graphql-$label")
        }
    }
    Capture-Metrics $RuntimeName "final"
    $manifest.runtimes[$RuntimeName] = [ordered]@{
        completed = $true
        startupSeconds = $startupSeconds
        receipts = $receipts
        completedAt = (Get-Date).ToUniversalTime().ToString("o")
    }
    Save-Manifest
    Invoke-BenchmarkCompose -Arguments @("--profile", $RuntimeName, "stop", $service)
    Invoke-BenchmarkCompose -Arguments @("--profile", $RuntimeName, "rm", "-f", $service)
}

Write-EnvironmentMetadata (Join-Path $run.Path "environment.json")
Save-Manifest

if ($DryRun) {
    [ordered]@{
        project = $script:BenchmarkProject
        composeArgs = Get-BenchmarkComposeArgs
        cleanupArgs = Get-BenchmarkDownArgs
        requestedRuntime = $Runtime
        fullLoad = [bool]$FullLoad
    } | ConvertTo-Json -Depth 5
    exit 0
}

try {
    Assert-BenchmarkCapacity
    Start-Infrastructure
    if ($Runtime -eq "Infrastructure") {
        $KeepInfrastructure = $true
    } elseif ($Runtime -eq "All") {
        Invoke-RuntimeRun "jvm"
        Invoke-RuntimeRun "native"
    } else {
        Invoke-RuntimeRun $Runtime
    }
} catch {
    $manifest.error = $_.Exception.Message
    throw
} finally {
    $manifest.completedAt = (Get-Date).ToUniversalTime().ToString("o")
    try {
        Write-BenchmarkComparison $run.Path
    } catch {
        $manifest.comparisonError = $_.Exception.Message
    }
    if (-not $KeepInfrastructure) {
        Stop-BenchmarkPlatform
        Assert-NoBenchmarkResources
        $manifest.cleanupVerified = $true
    }
    Save-Manifest
}
