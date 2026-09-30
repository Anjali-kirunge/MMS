<#
.SYNOPSIS
    Creates and seeds the `military_asset_management` MySQL database.

.DESCRIPTION
    Applies database\military_asset_management.sql. The script is destructive:
    it drops and recreates the schema, so every transaction and audit entry is
    removed. Run it whenever you want to return to the seeded demo state.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\setup-database.ps1
    powershell -ExecutionPolicy Bypass -File .\scripts\setup-database.ps1 -MysqlPassword "s3cret"

.DESCRIPTION
    Managed MySQL hosts such as Aiven require TLS, so pass -SslMode REQUIRED
    when the target is not a local server.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\setup-database.ps1 `
        -Host_ mysql-xxxx.region.aivencloud.com -Port 14711 `
        -Username avnadmin -Password "s3cret" -SslMode REQUIRED
#>
[CmdletBinding()]
param(
    [string]$MysqlExecutable = "mysql",
    [string]$Host_ = "localhost",
    [int]$Port = 3306,
    [string]$Username = "root",
    [string]$Password = "root",
    # Managed MySQL providers such as Aiven require TLS. Local MySQL does not.
    [ValidateSet("DISABLED", "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY")]
    [string]$SslMode = "DISABLED",
    [switch]$DropFirst
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$sqlFile = Join-Path $repoRoot "database\military_asset_management.sql"

if (-not (Test-Path -LiteralPath $sqlFile)) {
    throw "Schema file not found: $sqlFile"
}

if (-not (Get-Command $MysqlExecutable -ErrorAction SilentlyContinue)) {
    # @() keeps the result an array. Without it PowerShell collapses a single hit
    # to a plain string, and $candidates[0] would then return the first *character*
    # of the path instead of the path itself.
    $candidates = @(
        @("C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
          "C:\Program Files\MySQL\MySQL Server 8.1\bin\mysql.exe",
          "C:\xampp\mysql\bin\mysql.exe") | Where-Object { Test-Path -LiteralPath $_ }
    )
    if ($candidates.Count -eq 0) {
        throw "mysql client not found. Install it or pass -MysqlExecutable <path>."
    }
    $MysqlExecutable = $candidates[0]
}

$arguments = @(
    "-h", $Host_,
    "-P", $Port,
    "-u", $Username,
    "--default-character-set=utf8mb4"
)

if ($SslMode -ne "DISABLED") {
    $arguments += "--ssl-mode=$SslMode"
}

# Pass the password through MYSQL_PWD rather than -p<password> so it never shows
# up in the process command line and the client stops printing a warning.
$env:MYSQL_PWD = $Password
try {
    if ($DropFirst) {
        Write-Host "Dropping schema military_asset_management ..." -ForegroundColor Yellow
        & $MysqlExecutable @arguments -e "DROP DATABASE IF EXISTS military_asset_management;"
        if ($LASTEXITCODE -ne 0) { throw "Failed to drop the database." }
    }

    Write-Host "Applying $sqlFile ..." -ForegroundColor Cyan
    # Pipe the file through stdin instead of using `-e "source <path>"` so a repo
    # checked out under a path containing spaces still works.
    Get-Content -LiteralPath $sqlFile -Raw | & $MysqlExecutable @arguments
    if ($LASTEXITCODE -ne 0) { throw "Failed to apply the schema." }

    Write-Host ""
    & $MysqlExecutable @arguments -e @"
SELECT 'bases' AS entity, COUNT(*) AS rows_count FROM military_asset_management.bases
UNION ALL SELECT 'equipment_types', COUNT(*) FROM military_asset_management.equipment_types
UNION ALL SELECT 'users', COUNT(*) FROM military_asset_management.users
UNION ALL SELECT 'personnel', COUNT(*) FROM military_asset_management.personnel
UNION ALL SELECT 'stock_balances', COUNT(*) FROM military_asset_management.stock_balances;
"@
}
finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "Database ready. Start the backend with: mvn -f backend/pom.xml spring-boot:run" -ForegroundColor Green
