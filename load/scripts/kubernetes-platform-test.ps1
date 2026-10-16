param(
    [string]$KindExe = "kind",
    [string]$HelmExe = "helm",
    [string]$KubectlExe = "kubectl",
    [string]$ClusterName = "erg-platform-test",
    [switch]$KeepCluster
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Set-Location $repo
if ($ClusterName -ne "erg-platform-test") { throw "Refusing non-ERG test cluster: $ClusterName" }
foreach ($tool in @($KindExe, $HelmExe, $KubectlExe)) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "Required tool not found: $tool" }
}
if (-not (docker image inspect erg-spring:benchmark-jvm 2>$null)) {
    throw "Build erg-spring:benchmark-jvm before the Kubernetes platform test"
}

$runId = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$evidence = Join-Path $repo "load/results/kubernetes-$runId"
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$namespace = "erg-test"
$release = "erg"
$clusterCreated = $false

function Invoke-Kubectl([string[]]$Arguments) {
    & $KubectlExe --context "kind-$ClusterName" @Arguments
    if ($LASTEXITCODE -ne 0) { throw "kubectl failed: $($Arguments -join ' ')" }
}

function Wait-DeploymentAvailable([int]$TimeoutSeconds = 300) {
    Invoke-Kubectl @("-n", $namespace, "wait", "deployment/$release-erg-spring", "--for=jsonpath={.status.availableReplicas}=3", "--timeout=${TimeoutSeconds}s")
}

try {
    & $KindExe create cluster --name $ClusterName --config ops/kubernetes/kind/cluster.yaml
    if ($LASTEXITCODE -ne 0) { throw "kind cluster creation failed" }
    $clusterCreated = $true
    & $KindExe load docker-image erg-spring:benchmark-jvm --name $ClusterName
    if ($LASTEXITCODE -ne 0) { throw "kind image load failed" }

    Invoke-Kubectl @("create", "namespace", $namespace)
    & $KubectlExe --context "kind-$ClusterName" -n $namespace create configmap postgres-init `
        --from-file=ops/docker/postgres/init --dry-run=client -o yaml |
        & $KubectlExe --context "kind-$ClusterName" -n $namespace apply -f -
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL init ConfigMap creation failed" }

    $secretArgs = @(
        "--context", "kind-$ClusterName", "-n", $namespace, "create", "secret", "generic", "erg-spring-secrets",
        "--from-literal=SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/erg_kubernetes_test",
        "--from-literal=SPRING_DATASOURCE_USERNAME=erg_app",
        "--from-literal=SPRING_DATASOURCE_PASSWORD=erg-test-runtime",
        "--from-literal=SPRING_FLYWAY_USER=erg",
        "--from-literal=SPRING_FLYWAY_PASSWORD=erg-test-owner",
        "--from-literal=FLYWAY_ENABLED=true",
        "--from-literal=FLYWAY_LOCATIONS=classpath:db/migration/postgres",
        "--from-literal=SPRING_DATA_REDIS_URL=redis://:erg-test-state@redis-state:6379",
        "--from-literal=ERG_REDIS_STATE_ENABLED=true",
        "--from-literal=ERG_REDIS_STATE_URL=redis://:erg-test-state@redis-state:6379",
        "--from-literal=ERG_REDIS_CACHE_ENABLED=true",
        "--from-literal=ERG_REDIS_CACHE_URL=redis://:erg-test-cache@redis-cache:6379",
        "--from-literal=SECRET_AUTH__JWT_SECRET=kubernetes-test-jwt-secret-0123456789",
        "--from-literal=SECRET_AUTH__JWT_REFRESH_SECRET=kubernetes-test-refresh-secret-9876543210",
        "--from-literal=SECRET_MEDIA_URL_SIGNING_KEY=kubernetes-test-media-key-0123456789",
        "--from-literal=TENANT__ENABLED=true",
        "--from-literal=AUTH__GEO_BLOCK_ENABLED=false",
        "--from-literal=ERG_PROCESS_LOCAL_ENABLED=false",
        "--from-literal=ERG_OUTBOX_WORKER_ENABLED=false",
        "--from-literal=ERG_REPORTING_WORKER_ENABLED=false",
        "--from-literal=NOTIFICATIONS__WORKER__ENABLED=false",
        "--from-literal=NOTIFICATIONS__PUSH__ENABLED=false",
        "--dry-run=client", "-o", "yaml"
    )
    & $KubectlExe @secretArgs | & $KubectlExe --context "kind-$ClusterName" -n $namespace apply -f -
    if ($LASTEXITCODE -ne 0) { throw "Application Secret creation failed" }

    Invoke-Kubectl @("-n", $namespace, "apply", "-f", "ops/kubernetes/kind/dependencies.yaml")
    foreach ($dependency in @("postgres", "redis-state", "redis-cache")) {
        Invoke-Kubectl @("-n", $namespace, "rollout", "status", "deployment/$dependency", "--timeout=300s")
    }

    # helm upgrade --install is intentionally the sole release mutation path.
    & $HelmExe lint helm/erg-spring -f ops/kubernetes/kind/values.yaml
    if ($LASTEXITCODE -ne 0) { throw "Helm lint failed" }
    & $HelmExe template $release helm/erg-spring -n $namespace -f ops/kubernetes/kind/values.yaml |
        Set-Content -Encoding utf8 (Join-Path $evidence "rendered.yaml")
    & $HelmExe upgrade --install $release helm/erg-spring -n $namespace -f ops/kubernetes/kind/values.yaml --wait --timeout 10m
    if ($LASTEXITCODE -ne 0) { throw "Helm deployment failed" }
    Invoke-Kubectl @("-n", $namespace, "rollout", "status", "deployment/$release-erg-spring", "--timeout=600s")

    Invoke-Kubectl @("-n", $namespace, "get", "pods", "-o", "wide") |
        Set-Content -Encoding utf8 (Join-Path $evidence "pods-initial.txt")
    Invoke-Kubectl @("get", "nodes", "-o", "wide") |
        Set-Content -Encoding utf8 (Join-Path $evidence "nodes.txt")

    Invoke-Kubectl @("-n", $namespace, "scale", "deployment/$release-erg-spring", "--replicas=3")
    Wait-DeploymentAvailable

    $victim = (& $KubectlExe --context "kind-$ClusterName" -n $namespace get pods `
        -l "app.kubernetes.io/instance=$release" -o jsonpath="{.items[0].metadata.name}" | Out-String).Trim()
    $podFailureStarted = Get-Date
    Invoke-Kubectl @("-n", $namespace, "delete", "pod", $victim, "--wait=false")
    Wait-DeploymentAvailable
    $podRecoverySeconds = ((Get-Date) - $podFailureStarted).TotalSeconds

    $rolloutOut = Join-Path $evidence "rollout.stdout.log"
    $probeScript = 'failures=0; requests=0; echo 0 >/tmp/failures; echo 0 >/tmp/requests; while true; do requests=$((requests+1)); wget -q -T 3 -O /dev/null http://erg-erg-spring:1606/ready || failures=$((failures+1)); echo $failures >/tmp/failures; echo $requests >/tmp/requests; sleep 0.25; done'
    Invoke-Kubectl @("-n", $namespace, "run", "rollout-probe", "--image=busybox:1.37.0", "--restart=Never", "--command", "--", "sh", "-c", $probeScript)
    Invoke-Kubectl @("-n", $namespace, "wait", "pod/rollout-probe", "--for=condition=Ready", "--timeout=120s")
    Invoke-Kubectl @("-n", $namespace, "set", "env", "deployment/$release-erg-spring", "ERG_ROLLOUT_RECEIPT=$runId")
    & $KubectlExe --context "kind-$ClusterName" -n $namespace rollout status `
        "deployment/$release-erg-spring" --timeout=600s 2>&1 |
        Tee-Object -FilePath $rolloutOut
    if ($LASTEXITCODE -ne 0) { throw "Rolling deployment failed" }
    $requestFailures = [int]((& $KubectlExe --context "kind-$ClusterName" -n $namespace exec rollout-probe -- cat /tmp/failures | Out-String).Trim())
    $requestCount = [int]((& $KubectlExe --context "kind-$ClusterName" -n $namespace exec rollout-probe -- cat /tmp/requests | Out-String).Trim())
    Invoke-Kubectl @("-n", $namespace, "delete", "pod", "rollout-probe", "--wait=true")
    if ($requestFailures -ne 0) { throw "Rolling deployment interrupted $requestFailures of $requestCount readiness requests" }

    $worker = "$ClusterName-worker3"
    $nodeFailureStarted = Get-Date
    Invoke-Kubectl @("drain", $worker, "--ignore-daemonsets", "--delete-emptydir-data", "--force", "--timeout=300s")
    Wait-DeploymentAvailable
    $nodeRecoverySeconds = ((Get-Date) - $nodeFailureStarted).TotalSeconds
    Invoke-Kubectl @("uncordon", $worker)

    Invoke-Kubectl @("-n", $namespace, "get", "deployment,pods,pdb", "-o", "wide") |
        Set-Content -Encoding utf8 (Join-Path $evidence "final-state.txt")
    [ordered]@{
        runId = $runId
        podRecoverySeconds = $podRecoverySeconds
        nodeRecoverySeconds = $nodeRecoverySeconds
        rollingReadinessRequests = $requestCount
        rollingReadinessFailures = $requestFailures
        completedAt = (Get-Date).ToUniversalTime().ToString("o")
    } | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $evidence "receipt.json")
} finally {
    if ($clusterCreated -and -not $KeepCluster) {
        # kind delete cluster is guarded by the exact ClusterName check above.
        & $KindExe delete cluster --name $ClusterName
    }
}
