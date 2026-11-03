# Shared guard for destructive PostgreSQL helpers (reset/clean). Dot-source it:
#   . (Join-Path $PSScriptRoot "postgres-target-guard.ps1")
#   Assert-LocalPostgresTarget -DatabaseUrl $url -AllowRemoteHost $AllowRemoteHost
#
# Destructive scripts only run against a local host (localhost, 127.0.0.1, ::1, *.localhost, *.test).
# Any other host is refused unless the operator passes -AllowRemoteHost with that exact host name.

function Get-PostgresUrlHost {
    param([string]$DatabaseUrl)

    if ($DatabaseUrl -match "^(?:jdbc:)?postgres(?:ql)?://(?:[^@/]*@)?(\[[^\]]+\]|[^/:?,]+)") {
        return $matches[1].Trim("[", "]").ToLowerInvariant()
    }
    return $null
}

function Test-LocalPostgresHost {
    param([string]$HostName)

    if ([string]::IsNullOrWhiteSpace($HostName)) {
        return $false
    }
    $name = $HostName.ToLowerInvariant()
    return $name -eq "localhost" -or $name -eq "127.0.0.1" -or $name -eq "::1" `
        -or $name.EndsWith(".localhost") -or $name.EndsWith(".test")
}

function Assert-LocalPostgresTarget {
    param(
        [string]$DatabaseUrl,
        [string]$AllowRemoteHost
    )

    $hostName = Get-PostgresUrlHost $DatabaseUrl
    if ($null -eq $hostName) {
        throw "Refusing to run: cannot parse the PostgreSQL host from the database URL."
    }
    if (Test-LocalPostgresHost $hostName) {
        return $hostName
    }
    if (-not [string]::IsNullOrWhiteSpace($AllowRemoteHost) -and
        $AllowRemoteHost.Trim().ToLowerInvariant() -eq $hostName) {
        Write-Warning "Remote PostgreSQL host explicitly allowed by -AllowRemoteHost: $hostName"
        return $hostName
    }
    throw "Refusing to run against non-local PostgreSQL host '$hostName'. " +
        "Pass -AllowRemoteHost $hostName (exact host) only for an approved, backed-up target."
}
