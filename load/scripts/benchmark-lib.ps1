$ErrorActionPreference = "Stop"

$script:BenchmarkProject = "erg-benchmark"
$script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$script:ComposeFile = Join-Path $script:RepoRoot "ops/docker/compose.benchmark.yml"
$script:EnvFile = Join-Path $script:RepoRoot "ops/docker/.env.benchmark.example"

function Assert-BenchmarkProject([string]$Name) {
    if ($Name -ne $script:BenchmarkProject) {
        throw "Refusing non-benchmark project: $Name"
    }
}

function Get-BenchmarkComposeArgs {
    @("--project-name", $script:BenchmarkProject, "--env-file", $script:EnvFile, "-f", $script:ComposeFile)
}

function Get-BenchmarkDownArgs {
    @((Get-BenchmarkComposeArgs) + @(
        "--profile", "jvm",
        "--profile", "native",
        "--profile", "load",
        "down", "-v", "--remove-orphans"
    ))
}

function Invoke-BenchmarkCompose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    Assert-BenchmarkProject $script:BenchmarkProject
    & docker compose @(Get-BenchmarkComposeArgs) @Arguments
    if ($LASTEXITCODE -ne 0) { throw "docker compose failed: $($Arguments -join ' ')" }
}

function Invoke-BenchmarkMeasuredStep(
    [Parameter(Mandatory)][scriptblock]$Operation,
    [Parameter(Mandatory)][scriptblock]$EvidenceCapture
) {
    $operationFailure = $null
    $captureFailure = $null
    try { & $Operation } catch { $operationFailure = $_ }
    try { & $EvidenceCapture } catch { $captureFailure = $_ }
    if ($null -ne $operationFailure) {
        if ($null -ne $captureFailure) {
            throw "Benchmark step failed: $($operationFailure.Exception.Message); evidence capture also failed: $($captureFailure.Exception.Message)"
        }
        throw $operationFailure
    }
    if ($null -ne $captureFailure) { throw $captureFailure }
}

function Stop-BenchmarkPlatform {
    Assert-BenchmarkProject $script:BenchmarkProject
    & docker compose @(Get-BenchmarkDownArgs)
    if ($LASTEXITCODE -ne 0) { throw "benchmark cleanup failed" }
}

function Assert-NoBenchmarkResources {
    $filters = @(
        @("ps", "-aq", "--filter", "label=com.docker.compose.project=$script:BenchmarkProject"),
        @("network", "ls", "-q", "--filter", "label=com.docker.compose.project=$script:BenchmarkProject"),
        @("volume", "ls", "-q", "--filter", "label=com.docker.compose.project=$script:BenchmarkProject")
    )
    foreach ($arguments in $filters) {
        $remaining = @(& docker @arguments)
        if ($LASTEXITCODE -ne 0) { throw "Docker resource verification failed: $($arguments -join ' ')" }
        if ($remaining.Count -gt 0) {
            throw "Benchmark resources remain after cleanup: $($remaining -join ', ')"
        }
    }
}

function New-BenchmarkRunDirectory {
    $runId = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
    $path = Join-Path $script:RepoRoot "load/results/$runId"
    New-Item -ItemType Directory -Force -Path $path | Out-Null
    foreach ($runtime in @("jvm", "native")) {
        New-Item -ItemType Directory -Force -Path (Join-Path $path "$runtime/k6") | Out-Null
        New-Item -ItemType Directory -Force -Path (Join-Path $path "$runtime/metrics") | Out-Null
    }
    return @{ Id = $runId; Path = $path }
}

function Write-EnvironmentMetadata([string]$OutputPath) {
    $dockerVersion = (& docker version --format "{{json .}}" | Out-String).Trim()
    $dockerInfo = (& docker info --format "{{json .}}" | Out-String).Trim()
    $commit = (& git -C $script:RepoRoot rev-parse HEAD | Out-String).Trim()
    $dirty = (& git -C $script:RepoRoot status --porcelain=v1 | Out-String)
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        $dirtyHash = $sha256.ComputeHash([Text.Encoding]::UTF8.GetBytes($dirty))
    } finally {
        $sha256.Dispose()
    }
    $metadata = [ordered]@{
        recordedAt = (Get-Date).ToUniversalTime().ToString("o")
        commit = $commit
        dirty = -not [string]::IsNullOrWhiteSpace($dirty)
        dirtyDigest = (($dirtyHash | ForEach-Object { $_.ToString("x2") }) -join "")
        os = [Environment]::OSVersion.VersionString
        processors = [Environment]::ProcessorCount
        dockerVersion = ($dockerVersion | ConvertFrom-Json)
        dockerInfo = ($dockerInfo | ConvertFrom-Json)
    }
    $metadata | ConvertTo-Json -Depth 20 | Set-Content -Encoding utf8 $OutputPath
}

function Assert-BenchmarkCapacity {
    $memory = [int64]((& docker info --format "{{.MemTotal}}" | Out-String).Trim())
    if ($memory -lt 16GB) {
        throw "At least 16 GiB Docker memory is required; detected $memory bytes"
    }
}

function Wait-BenchmarkServiceHealthy([string]$Service, [int]$TimeoutSeconds = 300) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $id = (& docker compose @(Get-BenchmarkComposeArgs) ps -q $Service | Out-String).Trim()
        if ($id) {
            $status = (& docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" $id | Out-String).Trim()
            if ($status -eq "healthy" -or $status -eq "running") { return }
            if ($status -eq "unhealthy" -or $status -eq "exited") {
                throw "$Service entered terminal status $status"
            }
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $Service"
}

function Wait-BenchmarkHttpReady([string]$Uri, [string]$Name, [int]$TimeoutSeconds = 120) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) { return }
        } catch {
            # The service may still be starting; retry until the bounded deadline.
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $Name at $Uri"
}

function Invoke-BenchmarkPrometheusQuery([string]$Query) {
    $encoded = [Uri]::EscapeDataString($Query)
    Invoke-RestMethod -Uri "http://127.0.0.1:19090/api/v1/query?query=$encoded" -TimeoutSec 10
}

function Assert-BenchmarkPrometheusSeries([string]$Query, [string]$Description) {
    $response = Invoke-BenchmarkPrometheusQuery $Query
    if ($response.status -ne "success" -or @($response.data.result).Count -eq 0) {
        throw "Prometheus evidence missing for $Description ($Query)"
    }
    return $response
}

function Assert-BenchmarkPrometheusEndpoint(
    [ValidateSet("jvm", "native")][string]$Runtime
) {
    $backendService = Get-BenchmarkRuntimeService $Runtime
    $status = (& docker compose @(Get-BenchmarkComposeArgs) --profile $Runtime exec -T $backendService `
        curl --silent --show-error --output /dev/null --write-out "%{http_code}" `
        http://127.0.0.1:1606/actuator/prometheus | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $status -ne "200") {
        throw "Prometheus endpoint gate failed for $backendService (HTTP $status)"
    }
}

function Get-BenchmarkGrafanaHeaders {
    $password = if ($env:GRAFANA_PASSWORD) { $env:GRAFANA_PASSWORD } else { "erg-benchmark-grafana" }
    $token = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("benchmark:$password"))
    @{ Authorization = "Basic $token" }
}

function Assert-GrafanaBenchmarkProvisioning {
    $headers = Get-BenchmarkGrafanaHeaders
    $health = Invoke-RestMethod -Uri "http://127.0.0.1:13000/api/health" -TimeoutSec 10
    if ($health.database -ne "ok") { throw "Grafana database health is not ok" }
    $datasource = Invoke-RestMethod -Headers $headers -Uri "http://127.0.0.1:13000/api/datasources/name/Prometheus" -TimeoutSec 10
    if ($datasource.type -ne "prometheus" -or $datasource.url -ne "http://prometheus:9090") {
        throw "Grafana Prometheus datasource is not provisioned correctly"
    }
    $dashboards = @(Invoke-RestMethod -Headers $headers -Uri "http://127.0.0.1:13000/api/search?type=dash-db" -TimeoutSec 10)
    if (-not ($dashboards | Where-Object { $_.uid -eq "erg-benchmark" })) {
        throw "Grafana benchmark dashboard is not provisioned"
    }
    [ordered]@{ health = $health; datasource = $datasource; dashboards = $dashboards }
}

function Wait-BenchmarkObservabilityReady(
    [switch]$RequireBackend,
    [ValidateSet("jvm", "native")][string]$Runtime = "jvm",
    [int]$TimeoutSeconds = 180
) {
    Wait-BenchmarkHttpReady "http://127.0.0.1:19090/-/ready" "Prometheus" $TimeoutSeconds
    Wait-BenchmarkHttpReady "http://127.0.0.1:13000/api/health" "Grafana" $TimeoutSeconds
    $backendService = Get-BenchmarkRuntimeService $Runtime
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            # Grafana reports database health before asynchronously provisioned datasources and
            # dashboards are necessarily queryable. Keep this assertion inside the same bounded
            # readiness loop as the Prometheus series instead of failing on the first healthy ping.
            Assert-GrafanaBenchmarkProvisioning | Out-Null
            Assert-BenchmarkPrometheusSeries 'up{job="cadvisor"} == 1' "cAdvisor target" | Out-Null
            if ($RequireBackend) {
                Assert-BenchmarkPrometheusEndpoint $Runtime
                Assert-BenchmarkPrometheusSeries "container_last_seen{container_label_com_docker_compose_project=`"erg-benchmark`",container_label_com_docker_compose_service=`"$backendService`"}" "cAdvisor $backendService series" | Out-Null
            }
            Assert-BenchmarkPrometheusSeries 'up{job="postgres"} == 1' "PostgreSQL exporter" | Out-Null
            Assert-BenchmarkPrometheusSeries 'count(up{job="redis"} == 1) == 2' "both Redis exporters" | Out-Null
            if ($RequireBackend) {
                Assert-BenchmarkPrometheusSeries 'up{job="erg-backend"} == 1' "backend scrape" | Out-Null
                Assert-BenchmarkPrometheusSeries 'hikaricp_connections{job="erg-backend"}' "Hikari pool" | Out-Null
                if ($Runtime -eq "jvm") {
                    Assert-BenchmarkPrometheusSeries 'jvm_memory_used_bytes{job="erg-backend"}' "JVM memory" | Out-Null
                }
            }
            return
        } catch {
            if ((Get-Date) -ge $deadline) { throw }
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for validated observability evidence"
}

function Write-BenchmarkObservabilityEvidence(
    [string]$Output,
    [string]$Label,
    [ValidateSet("jvm", "native")][string]$Runtime = "jvm"
) {
    New-Item -ItemType Directory -Force -Path $Output | Out-Null
    $backendService = "backend-$Runtime"
    $queries = [ordered]@{
        targets = 'up{job=~"erg-backend|cadvisor|postgres|redis"}'
        hikari = 'hikaricp_connections{job="erg-backend"}'
        hikariPending = 'hikaricp_connections_pending{job="erg-backend"}'
        containerMemory = "container_memory_working_set_bytes{container_label_com_docker_compose_project=`"erg-benchmark`",container_label_com_docker_compose_service=`"$backendService`"}"
        containerCpu = "rate(container_cpu_usage_seconds_total{container_label_com_docker_compose_project=`"erg-benchmark`",container_label_com_docker_compose_service=`"$backendService`"}[1m])"
        postgres = 'pg_stat_database_numbackends{datname="erg_benchmark"}'
        redisMemory = 'redis_memory_used_bytes'
    }
    if ($Runtime -eq "jvm") {
        $queries["jvmMemory"] = 'jvm_memory_used_bytes{job="erg-backend"}'
    }
    foreach ($entry in $queries.GetEnumerator()) {
        $response = Assert-BenchmarkPrometheusSeries $entry.Value $entry.Key
        $response | ConvertTo-Json -Depth 20 | Set-Content -Encoding utf8 (Join-Path $Output "prometheus-$($entry.Key)-$Label.json")
    }
    $grafana = Assert-GrafanaBenchmarkProvisioning
    $grafana | ConvertTo-Json -Depth 20 | Set-Content -Encoding utf8 (Join-Path $Output "grafana-provisioning-$Label.json")
}

function Write-BenchmarkImageMetadata([ValidateSet("jvm", "native")][string]$Runtime, [string]$OutputPath) {
    $image = "erg-spring:benchmark-$Runtime"
    $raw = (& docker image inspect $image | Out-String)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($raw)) {
        throw "Unable to inspect benchmark image $image"
    }
    $inspect = @($raw | ConvertFrom-Json)[0]
    [ordered]@{
        runtime = $Runtime
        image = $image
        id = $inspect.Id
        repoDigests = @($inspect.RepoDigests)
        created = $inspect.Created
        sizeBytes = [int64]$inspect.Size
        architecture = $inspect.Architecture
        os = $inspect.Os
        recordedAt = (Get-Date).ToUniversalTime().ToString("o")
    } | ConvertTo-Json -Depth 10 | Set-Content -Encoding utf8 $OutputPath
}

function Get-BenchmarkK6Receipt([IO.FileInfo]$File) {
    try { $summary = Get-Content -Raw $File.FullName | ConvertFrom-Json } catch { return $null }
    if ($null -eq $summary.metrics -or $null -eq $summary.metrics.http_req_duration) { return $null }
    $duration = $summary.metrics.http_req_duration
    $p99 = if ($null -ne $duration.'p(99)') { [double]$duration.'p(99)' } else { $null }
    if ($null -eq $p99) {
        $stdoutPath = [IO.Path]::ChangeExtension($File.FullName, ".stdout.log")
        if (Test-Path $stdoutPath) {
            $stdout = Get-Content -Raw $stdoutPath
            if ($stdout -match "p\(99\)=([0-9.]+)(ms|s)") {
                $p99 = [double]$Matches[1]
                if ($Matches[2] -eq "s") { $p99 *= 1000 }
            }
        }
    }
    $thresholdCrossed = @($duration.thresholds.PSObject.Properties.Value) -contains $true
    if ($summary.metrics.http_req_failed.thresholds) {
        $thresholdCrossed = $thresholdCrossed -or (@($summary.metrics.http_req_failed.thresholds.PSObject.Properties.Value) -contains $true)
    }
    $step = $null
    if ($File.BaseName -match 'vus-(\d+)') { $step = [int]$Matches[1] }
    [ordered]@{
        file = $File.Name
        scenario = if ($File.BaseName -match 'graphql') { "graphql" } else { "rest" }
        stepVus = $step
        smoke = $File.BaseName -match 'smoke'
        warmup = $File.BaseName -match 'warmup|profile'
        requests = [int64]$summary.metrics.http_reqs.count
        throughputRps = [double]$summary.metrics.http_reqs.rate
        p50Ms = [double]$(if ($null -ne $duration.'p(50)') { $duration.'p(50)' } else { $duration.med })
        p95Ms = [double]$duration.'p(95)'
        p99Ms = $p99
        failureRate = [double]$summary.metrics.http_req_failed.value
        thresholdsPassed = -not $thresholdCrossed
    }
}

function Write-BenchmarkComparison([string]$RunPath) {
    $result = [ordered]@{
        runId = Split-Path -Leaf $RunPath
        generatedAt = (Get-Date).ToUniversalTime().ToString("o")
        runtimes = [ordered]@{}
    }
    foreach ($runtime in @("jvm", "native")) {
        $runtimePath = Join-Path $RunPath $runtime
        $imagePath = Join-Path $runtimePath "image.json"
        $image = if (Test-Path $imagePath) { Get-Content -Raw $imagePath | ConvertFrom-Json } else { $null }
        $receipts = @()
        $k6Path = Join-Path $runtimePath "k6"
        if (Test-Path $k6Path) {
            foreach ($file in Get-ChildItem -LiteralPath $k6Path -Filter "*.json" | Sort-Object Name) {
                $receipt = Get-BenchmarkK6Receipt $file
                if ($null -ne $receipt) { $receipts += $receipt }
            }
        }
        $measured = @($receipts | Where-Object { -not $_.smoke -and -not $_.warmup -and $null -ne $_.stepVus })
        $result.runtimes[$runtime] = [ordered]@{
            image = $image
            receipts = $receipts
            measuredReceipts = $measured.Count
            passed = $measured.Count -gt 0 -and @($measured | Where-Object { -not $_.thresholdsPassed }).Count -eq 0
            complete = @($measured | Where-Object { $_.stepVus -eq 5000 }).Count -eq 2
        }
    }
    $result | ConvertTo-Json -Depth 20 | Set-Content -Encoding utf8 (Join-Path $RunPath "comparison.json")

    $lines = @(
        "# JVM vs GraalVM Native benchmark",
        "",
        "Run: $($result.runId)",
        "",
        "| Runtime | Scenario | VUs | Requests | RPS | p50 ms | p95 ms | p99 ms | Failure rate | Thresholds |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---|"
    )
    foreach ($runtime in @("jvm", "native")) {
        foreach ($receipt in $result.runtimes[$runtime].receipts | Where-Object { -not $_.smoke -and -not $_.warmup -and $null -ne $_.stepVus }) {
            $p99 = if ($null -eq $receipt.p99Ms) { "n/a" } else { "{0:N2}" -f $receipt.p99Ms }
            $lines += "| $runtime | $($receipt.scenario) | $($receipt.stepVus) | $($receipt.requests) | $(('{0:N2}' -f $receipt.throughputRps)) | $(('{0:N2}' -f $receipt.p50Ms)) | $(('{0:N2}' -f $receipt.p95Ms)) | $p99 | $(('{0:P3}' -f $receipt.failureRate)) | $(if ($receipt.thresholdsPassed) { 'PASS' } else { 'FAIL' }) |"
        }
    }
    $lines += ""
    foreach ($runtime in @("jvm", "native")) {
        $state = $result.runtimes[$runtime]
        $lines += "- ${runtime}: measured receipts=$($state.measuredReceipts), pass=$($state.passed), complete through 5,000 VUs=$($state.complete)."
    }
    $lines | Set-Content -Encoding utf8 (Join-Path $RunPath "comparison.md")
}

function Get-BenchmarkRuntimeService([ValidateSet("jvm", "native")][string]$Runtime) {
    "backend-$Runtime"
}

function Get-BenchmarkLoadSteps {
    @(
        [pscustomobject]@{ TargetVus = 100; Duration = "5m" },
        [pscustomobject]@{ TargetVus = 500; Duration = "5m" },
        [pscustomobject]@{ TargetVus = 1000; Duration = "10m" },
        [pscustomobject]@{ TargetVus = 5000; Duration = "30m" }
    )
}
