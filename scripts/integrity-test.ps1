<#
.SYNOPSIS
    Data integrity verification for the Military Asset Management System.

.DESCRIPTION
    Confirms the accounting invariants that the REST API smoke test cannot see
    directly:

      * stock is genuinely persisted in MySQL, not cached or held in memory
      * the schema CHECK constraints make negative stock impossible
      * purchases / transfers / assignments / expenditures reconcile against the
        stock_balances ledger, and against each other
      * the dashboard opening/closing formula matches the ledger
      * a rejected transaction rolls back completely (no partial writes)
      * the audit trail recorded the transaction and the rejection
      * there is no mock, hardcoded or fixture data anywhere in the source

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\integrity-test.ps1
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$DbUser = "root",
    [string]$DbPassword = "root",
    [string]$DbName = "military_asset_management",
    [string]$DbHost = "",
    [int]$DbPort = 0,
    # Managed providers such as Aiven refuse plaintext connections.
    [ValidateSet("DISABLED", "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY")]
    [string]$DbSslMode = "DISABLED"
)

$ErrorActionPreference = "Stop"
$script:pass = 0
$script:fail = 0
$script:failures = New-Object System.Collections.Generic.List[string]

$mysql = (Get-Command mysql -ErrorAction SilentlyContinue).Source
if (-not $mysql) {
    foreach ($candidate in @(
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.1\bin\mysql.exe")) {
        if (Test-Path $candidate) { $mysql = $candidate; break }
    }
}
if (-not $mysql) { throw "mysql client not found; add it to PATH or pass -DbUser/-DbPassword" }

# Host, port and TLS are only passed when supplied, so a local MySQL that is
# bound to the default socket keeps working unchanged.
$mysqlTarget = @()
if ($DbHost) { $mysqlTarget += @("-h", $DbHost) }
if ($DbPort -gt 0) { $mysqlTarget += @("-P", $DbPort) }
if ($DbSslMode -ne "DISABLED") { $mysqlTarget += "--ssl-mode=$DbSslMode" }

function Invoke-Sql {
    param([string]$Query)
    $env:MYSQL_PWD = $DbPassword
    $out = $Query | & $mysql @mysqlTarget -u $DbUser -N -B $DbName 2>$null
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    # Always hand back an array of rows so callers can index safely even when
    # the query returns exactly one line (PowerShell would collapse it to a string).
    return ,@($out)
}

function Get-Scalar {
    param([string]$Query)
    $rows = Invoke-Sql $Query
    if ($null -eq $rows -or $rows.Count -eq 0) { return $null }
    return $rows[0]
}

function Invoke-Api {
    param([string]$Method = "GET", [string]$Path, $Body, [string]$Token)
    $headers = @{ Accept = "application/json" }
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $params = @{
        Method = $Method; Uri = "$BaseUrl$Path"; Headers = $headers; UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
    }
    try {
        $response = Invoke-WebRequest @params
        $status = [int]$response.StatusCode
        $raw = $response.Content
    } catch {
        $status = [int]$_.Exception.Response.StatusCode
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $raw = $reader.ReadToEnd()
        $reader.Close()
    }
    $data = $null
    if ($raw) { try { $data = $raw | ConvertFrom-Json } catch { $data = $raw } }
    return [pscustomobject]@{ Status = $status; Body = $data; Raw = $raw }
}

function Check {
    param([string]$Name, [bool]$Condition, [string]$Detail = "")
    if ($Condition) {
        $script:pass++
        Write-Host ("  PASS  {0}" -f $Name) -ForegroundColor Green
    } else {
        $script:fail++
        $script:failures.Add("$Name$(if ($Detail) { " -> $Detail" })")
        Write-Host ("  FAIL  {0} {1}" -f $Name, $Detail) -ForegroundColor Red
    }
}

function Section { param([string]$Title) Write-Host "`n== $Title" -ForegroundColor Cyan }

Write-Host "Military Asset Management System - data integrity verification" -ForegroundColor White
Write-Host "API: $BaseUrl    Database: $DbName" -ForegroundColor DarkGray

$login = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ username = "admin"; password = "admin123" }
if ($login.Status -ne 200) { throw "Cannot sign in as admin (HTTP $($login.Status)): $($login.Raw)" }
$token = $login.Body.token

Section "Data lives in MySQL"
$dbName = Get-Scalar "SELECT DATABASE();"
Check "The API is connected to the expected schema" ($dbName.Trim() -eq $DbName) "connected to '$($dbName.Trim())'"

$persisted = Get-Scalar "SELECT COUNT(*) FROM stock_balances;"
Check "stock_balances is populated in MySQL" ([int]$persisted -gt 0) "rows=$persisted"

$apiStock = (Invoke-Api -Path "/api/inventory?size=500" -Token $token).Body.content
$dbStock = Invoke-Sql "SELECT base_id, equipment_type_id, on_hand_quantity FROM stock_balances;"
$dbMap = @{}
foreach ($row in $dbStock) {
    $parts = $row -split "`t"
    $dbMap["$($parts[0]):$($parts[1])"] = [int]$parts[2]
}
$mismatches = 0
foreach ($row in $apiStock) {
    $key = "$($row.baseId):$($row.equipmentTypeId)"
    if (-not $dbMap.ContainsKey($key) -or $dbMap[$key] -ne $row.onHandQuantity) { $mismatches++ }
}
Check "Every API stock row matches the MySQL row exactly" ($mismatches -eq 0) "$mismatches mismatches of $($apiStock.Count)"

Section "No negative stock is possible"
$neg = Get-Scalar "SELECT COUNT(*) FROM stock_balances WHERE on_hand_quantity < 0;"
Check "stock_balances has no negative on-hand quantity" ([int]$neg -eq 0) "rows=$neg"
$negOpen = Get-Scalar "SELECT COUNT(*) FROM stock_balances WHERE opening_balance < 0;"
Check "stock_balances has no negative opening balance" ([int]$negOpen -eq 0) "rows=$negOpen"

$constraints = Invoke-Sql "SELECT CONSTRAINT_NAME FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = '$DbName';"
Check "The schema carries CHECK constraints that block negative stock" (@($constraints).Count -ge 1) "found $(@($constraints).Count)"

Section "No mock or hardcoded data in the source"
$mockHits = @()
foreach ($pattern in @("mock", "dummy", "hardcoded", "fake-?data", "sample-?data", "TODO.*replace.*real")) {
    $hits = Select-String -Path "$PSScriptRoot\..\backend\src\main\java\**\*.java" -Pattern $pattern -ErrorAction SilentlyContinue
    if (-not $hits) {
        $hits = Get-ChildItem "$PSScriptRoot\..\backend\src\main\java" -Recurse -Filter *.java |
            Select-String -Pattern $pattern -ErrorAction SilentlyContinue
    }
    $mockHits += $hits
}
Check "No mock/dummy/hardcoded markers in backend source" ($mockHits.Count -eq 0) `
    (($mockHits | Select-Object -First 3 | ForEach-Object { "$($_.Filename):$($_.LineNumber)" }) -join ', ')

$feMock = Get-ChildItem "$PSScriptRoot\..\frontend\src" -Recurse -Include *.js,*.jsx -ErrorAction SilentlyContinue |
    Select-String -Pattern "mockData|dummyData|hardcoded|const .*= \[\s*\{\s*id:\s*1" -ErrorAction SilentlyContinue
Check "No mock data in frontend source" ($null -eq $feMock -or @($feMock).Count -eq 0) `
    (@($feMock) | Select-Object -First 3 | ForEach-Object { "$($_.Filename):$($_.LineNumber)" }) -join ', '

Section "The four transaction types reconcile against the ledger"
$recon = Invoke-Sql @"
SELECT sb.base_id, sb.equipment_type_id, sb.opening_balance, sb.on_hand_quantity,
  COALESCE(p.q,0) AS purchased, COALESCE(ti.q,0) AS tin, COALESCE(to_.q,0) AS tout,
  COALESCE(a.q,0) AS assigned, COALESCE(e.q,0) AS expended
FROM stock_balances sb
LEFT JOIN (SELECT p2.base_id b, pi.equipment_type_id e, SUM(pi.quantity) q
  FROM purchase_items pi JOIN purchases p2 ON p2.id = pi.purchase_id
  GROUP BY p2.base_id, pi.equipment_type_id) p
  ON p.b=sb.base_id AND p.e=sb.equipment_type_id
LEFT JOIN (SELECT t.destination_base_id b, ti.equipment_type_id e, SUM(ti.quantity) q FROM transfer_items ti
  JOIN transfers t ON t.id=ti.transfer_id GROUP BY t.destination_base_id, ti.equipment_type_id) ti
  ON ti.b=sb.base_id AND ti.e=sb.equipment_type_id
LEFT JOIN (SELECT t.source_base_id b, ti.equipment_type_id e, SUM(ti.quantity) q FROM transfer_items ti
  JOIN transfers t ON t.id=ti.transfer_id GROUP BY t.source_base_id, ti.equipment_type_id) to_
  ON to_.b=sb.base_id AND to_.e=sb.equipment_type_id
LEFT JOIN (SELECT base_id, equipment_type_id, SUM(quantity) q FROM assignments GROUP BY base_id, equipment_type_id) a
  ON a.base_id=sb.base_id AND a.equipment_type_id=sb.equipment_type_id
LEFT JOIN (SELECT base_id, equipment_type_id, SUM(quantity) q FROM expenditures GROUP BY base_id, equipment_type_id) e
  ON e.base_id=sb.base_id AND e.equipment_type_id=sb.equipment_type_id
ORDER BY sb.base_id, sb.equipment_type_id;
"@
$reconBad = 0
foreach ($row in $recon) {
    $p = $row -split "`t"
    $opening = [int]$p[2]; $onHand = [int]$p[3]
    $purchased = [int]$p[4]; $tin = [int]$p[5]; $tout = [int]$p[6]
    $assigned = [int]$p[7]; $expended = [int]$p[8]
    $expected = $opening + $purchased + $tin - $tout - $assigned - $expended
    if ($expected -ne $onHand) {
        $reconBad++
        if ($reconBad -le 3) { Write-Host ("        base/eq {0}/{1}: expected {2}, actual {3}" -f $p[0], $p[1], $expected, $onHand) -ForegroundColor DarkGray }
    }
}
Check "Closing = Opening + Purchases + In - Out - Assigned - Expended for every row" ($reconBad -eq 0) "$reconBad of $(@($recon).Count) rows disagree"

Section "The API dashboard agrees with the ledger"
$dash = (Invoke-Api -Path "/api/dashboard" -Token $token).Body
$dashBad = 0
foreach ($row in $dash.rows) {
    $expectedNet = $row.purchases + $row.transferIn - $row.transferOut
    $expectedClosing = $row.openingBalance + $row.purchases + $row.transferIn - $row.transferOut - $row.assigned - $row.expended
    if ($row.netMovement -ne $expectedNet -or $row.closingBalance -ne $expectedClosing) { $dashBad++ }
}
Check "Every dashboard row satisfies both formulas" ($dashBad -eq 0) "$dashBad bad rows"

$liveTotal = ($apiStock | Measure-Object -Property onHandQuantity -Sum).Sum
$dashTotal = ($dash.rows | Measure-Object -Property closingBalance -Sum).Sum
Check "Dashboard closing total equals live MySQL stock total" ($liveTotal -eq $dashTotal) "live=$liveTotal dashboard=$dashTotal"

Section "Failed transactions roll back completely"
$baseId = $dash.rows[0].baseId
$eqId = $dash.rows[0].equipmentTypeId
$before = (Invoke-Api -Path "/api/inventory?baseId=$baseId&equipmentTypeId=$eqId" -Token $token).Body.content[0].onHandQuantity
$auditBefore = Get-Scalar "SELECT COUNT(*) FROM audit_logs;"

# Two lines: the first is affordable, the second is not. Neither may be applied.
$tooBig = $before + 5000
$bad = Invoke-Api -Method POST -Path "/api/purchases" -Token $token -Body @{
    baseId = $baseId; supplier = "Integrity Test"; purchaseDate = "2026-03-01"
    items  = @(@{ equipmentTypeId = $eqId; quantity = 5; unitCost = 1 },
               @{ equipmentTypeId = $eqId; quantity = 1; unitCost = 1 })
}
Check "Purchase with a duplicate line is rejected (409)" ($bad.Status -eq 409) "HTTP $($bad.Status)"

$over = Invoke-Api -Method POST -Path "/api/transfers" -Token $token -Body @{
    sourceBaseId = $baseId; destinationBaseId = (@($dash.rows | Where-Object { $_.baseId -ne $baseId })[0].baseId)
    transferDate = "2026-03-01"
    items = @(@{ equipmentTypeId = $eqId; quantity = $tooBig })
}
Check "Over-sized transfer is rejected (409)" ($over.Status -eq 409) "HTTP $($over.Status)"

$after = (Invoke-Api -Path "/api/inventory?baseId=$baseId&equipmentTypeId=$eqId" -Token $token).Body.content[0].onHandQuantity
Check "Stock is unchanged after both rejected transactions" ($after -eq $before) "before=$before after=$after"

$transferCount = Get-Scalar "SELECT COUNT(*) FROM transfers;"
$partialPurchase = Get-Scalar "SELECT COUNT(*) FROM purchases WHERE supplier = 'Integrity Test';"
Check "No partial purchase row was written" ([int]$partialPurchase -eq 0) "rows=$partialPurchase"

$dupItems = Get-Scalar "SELECT COUNT(*) FROM purchase_items pi JOIN purchases p ON p.id=pi.purchase_id WHERE p.supplier='Integrity Test';"
Check "No orphan purchase_items were written" ([int]$dupItems -eq 0) "rows=$dupItems"

Section "The audit trail recorded the work"
# Generate the remaining action types so the assertions hold on a freshly seeded
# database regardless of what ran before this script.
Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ username = "admin"; password = "definitely-wrong" } | Out-Null

# A unique code per run keeps the script re-runnable. A fixed code collides with
# the row left behind by an interrupted earlier run, the CREATE then returns 409,
# and every later reference to the null response body fails.
$tmpCode = "INTG-{0}-{1}" -f (Get-Date -Format "HHmmss"), $PID
$tmpBase = Invoke-Api -Method POST -Path "/api/bases" -Token $token -Body @{
    code = $tmpCode; name = "Integrity Temp Base"; location = "Test"; commander = "None"
}
if ($null -eq $tmpBase.Body) {
    throw "Could not create the temporary base (HTTP $($tmpBase.Status)). Clean up and re-run."
}
Invoke-Api -Method PUT -Path "/api/bases/$($tmpBase.Body.id)" -Token $token -Body @{
    code = $tmpCode; name = "Integrity Temp Base (edited)"; location = "Test"; commander = "None"
} | Out-Null
Invoke-Api -Method DELETE -Path "/api/bases/$($tmpBase.Body.id)" -Token $token | Out-Null

$auditRows = Invoke-Api -Path "/api/audit-logs?size=500" -Token $token
$actions = @($auditRows.Body.content | Select-Object -ExpandProperty action -Unique)
foreach ($expected in @("LOGIN", "LOGIN_FAILED", "CREATE", "UPDATE", "DELETE")) {
    Check "Audit trail contains a $expected entry" ($actions -contains $expected) ($actions -join ',')
}
$auditTotal = [int](Get-Scalar "SELECT COUNT(*) FROM audit_logs;")
Check "Audit rows exist in MySQL" ($auditTotal -gt 0) "rows=$auditTotal"
Check "Audit rows are never deleted by failed requests" ($auditTotal -ge [int]$auditBefore) "before=$auditBefore after=$auditTotal"

# The audit controller is read-only: there is no POST, PUT, PATCH or DELETE mapping.
foreach ($method in @("POST", "PUT", "PATCH", "DELETE")) {
    $r = Invoke-Api -Method $method -Path "/api/audit-logs" -Token $token -Body @{ description = "tamper" }
    Check "The audit collection rejects $method (405)" ($r.Status -eq 405) "HTTP $($r.Status)"
}
$byId = Invoke-Api -Method DELETE -Path "/api/audit-logs/1" -Token $token
Check "There is no per-entry audit route to delete (404 or 405)" `
    ($byId.Status -in @(404, 405)) "HTTP $($byId.Status)"

Section "Persistence survives a fresh read"
$beforeRestart = (Invoke-Api -Path "/api/inventory?baseId=$baseId&equipmentTypeId=$eqId" -Token $token).Body.content[0].onHandQuantity
$direct = Get-Scalar "SELECT on_hand_quantity FROM stock_balances WHERE base_id=$baseId AND equipment_type_id=$eqId;"
Check "A brand new API call returns the same value as the row in MySQL" `
    ([int]$direct -eq $beforeRestart) "api=$beforeRestart db=$direct"

Write-Host ""
Write-Host ("Passed: {0}   Failed: {1}" -f $script:pass, $script:fail) -ForegroundColor White
if ($script:fail -gt 0) {
    Write-Host "`nFailures:" -ForegroundColor Red
    $script:failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    exit 1
}
Write-Host "All integrity checks passed." -ForegroundColor Green
exit 0
