$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "benchmark-lib.ps1")

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Assertion failed: $Message" }
}

$compose = (Get-BenchmarkComposeArgs) -join " "
Assert-True ($compose -match "erg-benchmark") "compose args must use erg-benchmark"

$down = (Get-BenchmarkDownArgs) -join " "
Assert-True ($down -match "down -v --remove-orphans") "cleanup must remove volumes and orphans"
Assert-True ($down -match "--profile jvm") "cleanup must include the JVM profile"
Assert-True ($down -match "--profile native") "cleanup must include the Native profile"
Assert-True ($down -match "--profile load") "cleanup must include the load profile"

$rejected = $false
try { Assert-BenchmarkProject "erg-dev" } catch { $rejected = $true }
Assert-True $rejected "non-benchmark projects must be rejected"

$harness = Get-Content -Raw (Join-Path $PSScriptRoot "benchmark.ps1")
$library = Get-Content -Raw (Join-Path $PSScriptRoot "benchmark-lib.ps1")
Assert-True ($harness -match 'Invoke-BenchmarkCompose -Arguments') `
    "compose flags must be passed through the explicit Arguments array so PowerShell does not consume -d/-e as common parameters"

$steps = @(Get-BenchmarkLoadSteps)
Assert-True ($steps.Count -eq 4) "measured load must emit four independent step receipts"
Assert-True (($steps.TargetVus -join ',') -eq '100,500,1000,5000') "load steps must be 100,500,1000,5000 VUs"
Assert-True (($steps.Duration -join ',') -eq '5m,5m,10m,30m') "load steps must keep the approved hold durations"
Assert-True ($harness.Contains('foreach ($step in Get-BenchmarkLoadSteps)')) `
    "full-load orchestration must execute each step independently"
Assert-True ($harness.Contains('"TARGET_VUS=$($step.TargetVus)"')) `
    "each k6 receipt must receive an explicit target VU count"
$warmupProfile = '"WARMUP=true", "TARGET_VUS=100", "STEP_DURATION=5m"'
Assert-True (([regex]::Matches($harness, [regex]::Escape($warmupProfile))).Count -eq 2) `
    "REST and GraphQL must each receive a production-path warmup long enough to exclude JVM JIT compilation from measured receipts"
Assert-True ($harness.Contains('Wait-BenchmarkObservabilityReady')) `
    "the harness must block load until Prometheus, Grafana, cAdvisor and backend metrics are queryable"
Assert-True ($harness.Contains('Write-BenchmarkObservabilityEvidence')) `
    "every measured step must persist validated backend/container/DB/cache monitoring evidence"
Assert-True ($library.Contains('Assert-GrafanaBenchmarkProvisioning')) `
    "the harness must verify the Grafana datasource and benchmark dashboard, not only the HTTP process"
$observabilityStart = $library.IndexOf('function Wait-BenchmarkObservabilityReady(')
$observabilityEnd = $library.IndexOf('function Write-BenchmarkObservabilityEvidence(', $observabilityStart)
Assert-True ($observabilityStart -ge 0 -and $observabilityEnd -gt $observabilityStart) `
    "the observability readiness function must remain discoverable by the harness contract"
$observabilityReady = $library.Substring($observabilityStart, $observabilityEnd - $observabilityStart)
Assert-True ($observabilityReady -match '(?s)do\s*\{.*?try\s*\{.*?Assert-GrafanaBenchmarkProvisioning') `
    "Grafana provisioning must be polled inside the bounded readiness loop because health can precede dashboard import"
Assert-True ($harness.Contains('--summary-trend-stats')) `
    "k6 summaries must retain p50/p95/p99 rather than leaving p99 only in console output"
Assert-True ($harness.Contains('Write-BenchmarkImageMetadata')) `
    "each runtime must persist image digest, size and creation metadata"
Assert-True ($harness.Contains('Write-BenchmarkComparison')) `
    "the harness must always project partial or complete comparison JSON and Markdown"
Assert-True ($library.Contains('$Runtime -eq "jvm"')) `
    "Native observability gates must not require JVM-only memory or GC series"
Assert-True ($library.Contains('http://127.0.0.1:1606/actuator/prometheus')) `
    "the backend gate must prove the Prometheus endpoint itself returns HTTP 200"
Assert-True ($library.Contains('container_label_com_docker_compose_service=`"$backendService`"')) `
    "cAdvisor readiness must prove a series for the selected backend runtime"
Assert-True ($library.Contains('count(up{job="redis"} == 1) == 2')) `
    "observability readiness must require both Redis exporters"

$script:evidenceCapturedAfterFailure = $false
$stepFailurePreserved = $false
try {
    Invoke-BenchmarkMeasuredStep `
        -Operation { throw "threshold crossed" } `
        -EvidenceCapture { $script:evidenceCapturedAfterFailure = $true }
} catch {
    $stepFailurePreserved = $_.Exception.Message -match "threshold crossed"
}
Assert-True $script:evidenceCapturedAfterFailure `
    "a failed k6 step must still capture its resource and dependency metrics"
Assert-True $stepFailurePreserved `
    "the original k6 threshold failure must remain fail-fast after evidence capture"

# PowerShell variable names are case-insensitive and script blocks use dynamic scope. A helper
# parameter named $Run therefore shadows benchmark.ps1's $run metadata object inside both blocks.
$run = [pscustomobject]@{ Path = "sentinel-run-path" }
$script:observedRunPath = $null
$script:observedCapturePath = $null
Invoke-BenchmarkMeasuredStep `
    -Operation { $script:observedRunPath = $run.Path } `
    -EvidenceCapture { $script:observedCapturePath = $run.Path }
Assert-True ($script:observedRunPath -eq "sentinel-run-path") `
    "measured-step execution must not shadow the outer benchmark run metadata"
Assert-True ($script:observedCapturePath -eq "sentinel-run-path") `
    "measured-step evidence capture must retain the outer benchmark run metadata"

Write-Output "benchmark-lib tests passed"
