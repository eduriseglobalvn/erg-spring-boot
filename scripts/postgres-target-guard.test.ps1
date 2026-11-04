# Self-check for postgres-target-guard.ps1 (P1-4). Run: powershell -NoProfile -File scripts/postgres-target-guard.test.ps1
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "postgres-target-guard.ps1")
$failures = 0
function Expect-Allowed($url, $allow) {
    try { Assert-LocalPostgresTarget -DatabaseUrl $url -AllowRemoteHost $allow 3>$null | Out-Null }
    catch { Write-Host "FAIL allowed: $url ($_)"; $script:failures++ }
}
function Expect-Refused($url, $allow) {
    try { Assert-LocalPostgresTarget -DatabaseUrl $url -AllowRemoteHost $allow 3>$null | Out-Null; Write-Host "FAIL refused: $url"; $script:failures++ }
    catch { }
}
Expect-Allowed "jdbc:postgresql://localhost:5432/erg" ""
Expect-Allowed "postgresql://u:p@127.0.0.1/erg" ""
Expect-Allowed "jdbc:postgresql://[::1]:5432/erg" ""
Expect-Allowed "jdbc:postgresql://db.test:5432/erg" ""
Expect-Allowed "jdbc:postgresql://staging.example.com:5432/erg" "staging.example.com"
Expect-Refused "jdbc:postgresql://prod.example.com:5432/erg" ""
Expect-Refused "jdbc:postgresql://prod.example.com:5432/erg" "other.example.com"
Expect-Refused "jdbc:postgresql://prod.example.com.evil:5432/erg" "prod.example.com"
Expect-Refused "jdbc:postgresql://localhost:5432,prod.example.com:5432/erg" ""
Expect-Refused "not-a-url" ""
if ($failures -gt 0) { Write-Host "$failures check(s) failed"; exit 1 }
Write-Host "postgres-target-guard: all checks passed"
