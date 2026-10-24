[CmdletBinding()]
param (
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Image,

    [switch]$NoCache
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  ERG Spring: Local Native Build & Push to Docker Hub" -ForegroundColor Cyan
Write-Host "  Image Target: $Image" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Kiểm tra Docker daemon
try {
    docker info > $null 2>&1
} catch {
    Write-Error "Docker Desktop chưa chạy! Vui lòng khởi động Docker Desktop trước khi chạy script."
    exit 1
}

# 2. Build Linux amd64 Native Image
$cacheArg = if ($NoCache) { "--no-cache" } else { "" }
Write-Host "`n[1/2] Đang build Native Image (Linux amd64 via GraalVM 25)..." -ForegroundColor Green
Write-Host "Lưu ý: Quá trình build tốn CPU/RAM và mất vài phút tuỳ máy dev." -ForegroundColor DarkGray

$buildCmd = "docker buildx build --platform linux/amd64 $cacheArg -t $Image --target native-runtime `"$root`""
Invoke-Expression $buildCmd

if ($LASTEXITCODE -ne 0) {
    Write-Error "Build Native Image thất bại!"
    exit $LASTEXITCODE
}

# 3. Push lên Docker Hub / Registry
Write-Host "`n[2/2] Đang push image lên Docker Hub..." -ForegroundColor Green
docker push $Image

if ($LASTEXITCODE -ne 0) {
    Write-Error "Push image lên Docker Hub thất bại! Hãy kiểm tra 'docker login'."
    exit $LASTEXITCODE
}

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "  Push hoàn tất thành công!" -ForegroundColor Green
Write-Host "  Server Watchtower sẽ tự động phát hiện và cập nhật" -ForegroundColor Cyan
Write-Host "  Backend container trên server trong vòng ~30 giây!" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Green
