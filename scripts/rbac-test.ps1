<#
.SYNOPSIS
    Role-based access control verification for the Military Asset Management System.

.DESCRIPTION
    Probes the real backend REST API with a live token for every role and asserts
    the expected allow / deny decision for every endpoint. This is deliberately
    separate from scripts\smoke-test.ps1, which covers accounting and CRUD
    behaviour: this script exists to prove that authorization is enforced by the
    backend, not merely hidden in the frontend.

    Endpoint matrix
      ADMIN             - full access to every base and every module
      BASE_COMMANDER    - read/write on their own base only; every cross-base
                          request must be rejected
      LOGISTICS_OFFICER - purchases, transfers, inventory, dashboard and read-only
                          reference data; blocked from users, personnel,
                          assignments, expenditures, bases and equipment types

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\rbac-test.ps1
    powershell -ExecutionPolicy Bypass -File .\scripts\rbac-test.ps1 -BaseUrl https://mams-backend.onrender.com
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$script:pass = 0
$script:fail = 0
$script:failures = New-Object System.Collections.Generic.List[string]

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

function Login {
    param([string]$Username, [string]$Password)
    $r = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ username = $Username; password = $Password }
    if ($r.Status -ne 200) { throw "Login failed for $Username (HTTP $($r.Status)): $($r.Raw)" }
    return $r.Body
}

# Asserts the exact status for one (role, method, path) combination.
function Expect {
    param([string]$Role, [string]$Method, [string]$Path, [int]$Status, $Body, [string]$Token, [string]$Note = "")
    $r = Invoke-Api -Method $Method -Path $Path -Body $Body -Token $Token
    Check ("{0,-17} {1,-6} {2,-52} -> {3}" -f $Role, $Method, $Path, $Status) `
        ($r.Status -eq $Status) "expected $Status, got $($r.Status): $(($r.Raw | Out-String).Trim())"
}

Write-Host "Military Asset Management System - RBAC verification against $BaseUrl" -ForegroundColor White

Section "Sign in as each role"
$admin = Login "admin" "admin123"
$adminToken = $admin.token
Check "ADMIN signs in with role ADMIN" ($admin.user.role -eq "ADMIN") $admin.user.role

$commander = Login "gen.alpha" "commander123"
$commanderToken = $commander.token
$myBase = $commander.user.baseId
Check "BASE_COMMANDER signs in and is bound to a base" ($commander.user.role -eq "BASE_COMMANDER" -and $null -ne $myBase) `
    "role=$($commander.user.role) baseId=$myBase"

$logistics = Login "logistics" "logistics123"
$logisticsToken = $logistics.token
Check "LOGISTICS_OFFICER signs in with role LOGISTICS_OFFICER" ($logistics.user.role -eq "LOGISTICS_OFFICER") $logistics.user.role

$allBases = (Invoke-Api -Path "/api/bases" -Token $adminToken).Body
$otherBaseId = @($allBases | Where-Object { $_.id -ne $myBase })[0].id
Check "A second base is available for the cross-base probes" ($null -ne $otherBaseId) "otherBaseId=$otherBaseId"

$allPersonnel = (Invoke-Api -Path "/api/personnel" -Token $adminToken).Body
$foreignPersonnel = @($allPersonnel | Where-Object { $_.baseId -ne $myBase })
$ownPersonnel = @($allPersonnel | Where-Object { $_.baseId -eq $myBase })
Check "Seeded personnel exist on the commander's base and elsewhere" `
    ($ownPersonnel.Count -ge 1 -and $foreignPersonnel.Count -ge 1) `
    "own=$($ownPersonnel.Count) foreign=$($foreignPersonnel.Count)"

$anyBaseId = $otherBaseId
$anyTypeId = (Invoke-Api -Path "/api/equipment-types" -Token $adminToken).Body[0].id

Section "Unauthenticated access is rejected everywhere"
foreach ($path in @("/api/dashboard", "/api/inventory", "/api/purchases", "/api/transfers",
        "/api/assignments", "/api/expenditures", "/api/audit-logs", "/api/users",
        "/api/bases", "/api/equipment-types", "/api/personnel")) {
    Expect "anonymous" "GET" $path 401
}

Section "ADMIN has full access"
Expect "ADMIN" "GET"  "/api/dashboard"                        200 $null $adminToken
Expect "ADMIN" "GET"  "/api/inventory?baseId=$otherBaseId"   200 $null $adminToken
Expect "ADMIN" "GET"  "/api/purchases?baseId=$otherBaseId"   200 $null $adminToken
Expect "ADMIN" "GET"  "/api/transfers?baseId=$otherBaseId"   200 $null $adminToken
Expect "ADMIN" "GET"  "/api/assignments?baseId=$otherBaseId" 200 $null $adminToken
Expect "ADMIN" "GET"  "/api/expenditures?baseId=$otherBaseId" 200 $null $adminToken
Expect "ADMIN" "GET"  "/api/personnel?baseId=$otherBaseId"   200 $null $adminToken
Expect "ADMIN" "GET"  "/api/audit-logs?size=5"              200 $null $adminToken
Expect "ADMIN" "GET"  "/api/users?size=5"                    200 $null $adminToken
Expect "ADMIN" "GET"  "/api/users/roles"                     200 $null $adminToken
Expect "ADMIN" "GET"  "/api/bases"                           200 $null $adminToken
Expect "ADMIN" "GET"  "/api/equipment-types"                 200 $null $adminToken

$adminBase = Invoke-Api -Method POST -Path "/api/bases" -Token $adminToken -Body @{
    code = "RBAC-TMP"; name = "RBAC Temporary Base"; location = "Test"; commander = "None"
}
Check "ADMIN can create a base (201)" ($adminBase.Status -eq 201) "HTTP $($adminBase.Status) $($adminBase.Raw)"
Expect "ADMIN" "DELETE" "/api/bases/$($adminBase.Body.id)" 204 $null $adminToken

$adminType = Invoke-Api -Method POST -Path "/api/equipment-types" -Token $adminToken -Body @{
    code = "RBAC-TMP"; name = "RBAC Temporary Type"; category = "Test"; unit = "pcs"; description = "RBAC"
}
Check "ADMIN can create an equipment type (201)" ($adminType.Status -eq 201) "HTTP $($adminType.Status)"
Expect "ADMIN" "DELETE" "/api/equipment-types/$($adminType.Body.id)" 204 $null $adminToken

$adminUser = Invoke-Api -Method POST -Path "/api/users" -Token $adminToken -Body @{
    username = "rbac.tmp"; password = "rbac12345"; fullName = "RBAC Temporary"; role = "LOGISTICS_OFFICER"
}
Check "ADMIN can create a user (201)" ($adminUser.Status -eq 201) "HTTP $($adminUser.Status) $($adminUser.Raw)"
Expect "ADMIN" "DELETE" "/api/users/$($adminUser.Body.id)" 204 $null $adminToken

Section "BASE_COMMANDER is confined to their own base"
Expect "BASE_COMMANDER" "GET" "/api/dashboard"                       200 $null $commanderToken
Expect "BASE_COMMANDER" "GET" "/api/inventory"                      200 $null $commanderToken
Expect "BASE_COMMANDER" "GET" "/api/purchases"                      200 $null $commanderToken
Expect "BASE_COMMANDER" "GET" "/api/transfers"                      200 $null $commanderToken
Expect "BASE_COMMANDER" "GET" "/api/assignments"                    200 $null $commanderToken
Expect "BASE_COMMANDER" "GET" "/api/expenditures"                   200 $null $commanderToken
Expect "BASE_COMMANDER" "GET" "/api/personnel"                      200 $null $commanderToken
Expect "BASE_COMMANDER" "GET" "/api/audit-logs?size=5"              200 $null $commanderToken
Expect "BASE_COMMANDER" "GET" "/api/bases"                          200 $null $commanderToken
Expect "BASE_COMMANDER" "GET" "/api/equipment-types"                200 $null $commanderToken

Section "BASE_COMMANDER cross-base requests are rejected"
Expect "BASE_COMMANDER" "GET" "/api/dashboard?baseId=$otherBaseId"            403 $null $commanderToken
Expect "BASE_COMMANDER" "GET" "/api/inventory?baseId=$otherBaseId"            403 $null $commanderToken
Expect "BASE_COMMANDER" "GET" "/api/purchases?baseId=$otherBaseId"            403 $null $commanderToken
Expect "BASE_COMMANDER" "GET" "/api/transfers?baseId=$otherBaseId"            403 $null $commanderToken
Expect "BASE_COMMANDER" "GET" "/api/assignments?baseId=$otherBaseId"          403 $null $commanderToken
Expect "BASE_COMMANDER" "GET" "/api/expenditures?baseId=$otherBaseId"         403 $null $commanderToken
Expect "BASE_COMMANDER" "GET" "/api/personnel?baseId=$otherBaseId"            403 $null $commanderToken
Expect "BASE_COMMANDER" "GET" "/api/audit-logs?baseId=$otherBaseId"           403 $null $commanderToken

# Passing no baseId at all must silently scope to the commander's own base.
$unfiltered = Invoke-Api -Path "/api/dashboard" -Token $commanderToken
$unfilteredBases = @($unfiltered.Body.rows | Select-Object -ExpandProperty baseId -Unique)
Check "BASE_COMMANDER dashboard with no baseId returns only their base" `
    ($unfilteredBases.Count -eq 1 -and $unfilteredBases[0] -eq $myBase) "bases=$($unfilteredBases -join ',')"

$unfilteredInventory = Invoke-Api -Path "/api/inventory?size=500" -Token $commanderToken
$inventoryBases = @($unfilteredInventory.Body.content | Select-Object -ExpandProperty baseId -Unique)
Check "BASE_COMMANDER inventory with no baseId returns only their base" `
    ($inventoryBases.Count -eq 1 -and $inventoryBases[0] -eq $myBase) "bases=$($inventoryBases -join ',')"

$unfilteredPersonnel = Invoke-Api -Path "/api/personnel" -Token $commanderToken
$personnelBases = @($unfilteredPersonnel.Body | Select-Object -ExpandProperty baseId -Unique)
Check "BASE_COMMANDER personnel with no baseId returns only their base" `
    ($personnelBases.Count -eq 1 -and $personnelBases[0] -eq $myBase) "bases=$($personnelBases -join ',')"

$unfilteredAudit = Invoke-Api -Path "/api/audit-logs?size=200" -Token $commanderToken
$auditBases = @($unfilteredAudit.Body.content | Where-Object { $null -ne $_.baseId } | Select-Object -ExpandProperty baseId -Unique)
Check "BASE_COMMANDER audit view contains no other base" `
    (@($auditBases | Where-Object { $_ -ne $myBase }).Count -eq 0) "bases=$($auditBases -join ',')"

Section "BASE_COMMANDER cannot reach another base by guessing an id"
if ($foreignPersonnel.Count -ge 1) {
    $fp = $foreignPersonnel[0]
    $r = Invoke-Api -Method PUT -Path "/api/personnel/$($fp.id)" -Token $commanderToken -Body @{
        serviceNumber = $fp.serviceNumber; fullName = "Hijacked"; baseId = $myBase; rankTitle = $fp.rankTitle
    }
    Check "BASE_COMMANDER cannot edit foreign personnel by id (403)" ($r.Status -eq 403) "HTTP $($r.Status)"
}
if ($foreignPersonnel.Count -ge 2) {
    $r = Invoke-Api -Method DELETE -Path "/api/personnel/$($foreignPersonnel[1].id)" -Token $commanderToken
    Check "BASE_COMMANDER cannot delete foreign personnel by id (403)" ($r.Status -eq 403) "HTTP $($r.Status)"
}
$foreignPurchase = @((Invoke-Api -Path "/api/purchases?size=200" -Token $adminToken).Body.content |
    Where-Object { $_.baseId -ne $myBase }) | Select-Object -First 1
if ($foreignPurchase) {
    $r = Invoke-Api -Path "/api/purchases/$($foreignPurchase.id)" -Token $commanderToken
    Check "BASE_COMMANDER cannot read a foreign purchase by id (403)" ($r.Status -eq 403) "HTTP $($r.Status)"
}
$foreignExpenditure = @((Invoke-Api -Path "/api/expenditures?size=200" -Token $adminToken).Body.content |
    Where-Object { $_.baseId -ne $myBase }) | Select-Object -First 1
if ($foreignExpenditure) {
    $r = Invoke-Api -Path "/api/expenditures/$($foreignExpenditure.id)" -Token $commanderToken
    Check "BASE_COMMANDER cannot read a foreign expenditure by id (403)" ($r.Status -eq 403) "HTTP $($r.Status)"
}
$foreignAssignment = @((Invoke-Api -Path "/api/assignments?size=200" -Token $adminToken).Body.content |
    Where-Object { $_.baseId -ne $myBase }) | Select-Object -First 1
if ($foreignAssignment) {
    $r = Invoke-Api -Path "/api/assignments/$($foreignAssignment.id)" -Token $commanderToken
    Check "BASE_COMMANDER cannot read a foreign assignment by id (403)" ($r.Status -eq 403) "HTTP $($r.Status)"
}
$foreignTransfer = @((Invoke-Api -Path "/api/transfers?size=200" -Token $adminToken).Body.content |
    Where-Object { $_.sourceBaseId -ne $myBase -and $_.destinationBaseId -ne $myBase }) | Select-Object -First 1
if ($foreignTransfer) {
    $r = Invoke-Api -Path "/api/transfers/$($foreignTransfer.id)" -Token $commanderToken
    Check "BASE_COMMANDER cannot read a wholly foreign transfer by id (403)" ($r.Status -eq 403) "HTTP $($r.Status)"
}

Section "BASE_COMMANDER is blocked from user administration"
Expect "BASE_COMMANDER" "GET"    "/api/users"        403 $null $commanderToken
Expect "BASE_COMMANDER" "GET"    "/api/users/roles"  403 $null $commanderToken
Expect "BASE_COMMANDER" "POST"   "/api/users"        403 @{ username = "cmd.attempt"; password = "commander123"; fullName = "No"; role = "LOGISTICS_OFFICER" } $commanderToken
Expect "BASE_COMMANDER" "POST"   "/api/bases"        403 @{ code = "CMDX"; name = "Commander Base"; location = "x"; commander = "y" } $commanderToken
Expect "BASE_COMMANDER" "POST"   "/api/equipment-types" 403 @{ code = "CMDX"; name = "Commander Type"; category = "x"; unit = "pcs" } $commanderToken
Expect "BASE_COMMANDER" "PUT"    "/api/bases/$myBase" 403 @{ code = $allBases[0].code; name = "Renamed" } $commanderToken

Section "LOGISTICS_OFFICER permissions"
Expect "LOGISTICS_OFFICER" "GET"  "/api/purchases"      200 $null $logisticsToken
Expect "LOGISTICS_OFFICER" "GET"  "/api/transfers"      200 $null $logisticsToken
Expect "LOGISTICS_OFFICER" "GET"  "/api/inventory"      200 $null $logisticsToken
Expect "LOGISTICS_OFFICER" "GET"  "/api/dashboard"      200 $null $logisticsToken
Expect "LOGISTICS_OFFICER" "GET"  "/api/audit-logs?size=5" 200 $null $logisticsToken
Expect "LOGISTICS_OFFICER" "GET"  "/api/bases"          200 $null $logisticsToken
Expect "LOGISTICS_OFFICER" "GET"  "/api/equipment-types" 200 $null $logisticsToken

Section "LOGISTICS_OFFICER is blocked from the restricted modules"
Expect "LOGISTICS_OFFICER" "GET"    "/api/users"        403 $null $logisticsToken
Expect "LOGISTICS_OFFICER" "GET"    "/api/users/roles"  403 $null $logisticsToken
Expect "LOGISTICS_OFFICER" "POST"   "/api/users"        403 @{ username = "log.attempt"; password = "logistics123"; fullName = "No"; role = "ADMIN" } $logisticsToken
Expect "LOGISTICS_OFFICER" "GET"    "/api/personnel"    403 $null $logisticsToken
Expect "LOGISTICS_OFFICER" "GET"    "/api/personnel/search" 403 $null $logisticsToken
Expect "LOGISTICS_OFFICER" "POST"   "/api/personnel"    403 @{ serviceNumber = "X-1"; fullName = "No"; baseId = $anyBaseId } $logisticsToken
Expect "LOGISTICS_OFFICER" "GET"    "/api/assignments"  403 $null $logisticsToken
Expect "LOGISTICS_OFFICER" "POST"   "/api/assignments"  403 @{ baseId = $anyBaseId; personnelId = $ownPersonnel[0].id; equipmentTypeId = $anyTypeId; quantity = 1; assignedDate = "2026-02-01" } $logisticsToken
Expect "LOGISTICS_OFFICER" "GET"    "/api/expenditures" 403 $null $logisticsToken
Expect "LOGISTICS_OFFICER" "POST"   "/api/expenditures" 403 @{ baseId = $anyBaseId; equipmentTypeId = $anyTypeId; quantity = 1; expendedDate = "2026-02-01"; reason = "Test" } $logisticsToken
Expect "LOGISTICS_OFFICER" "POST"   "/api/bases"        403 @{ code = "LOGX"; name = "Logistics Base"; location = "x"; commander = "y" } $logisticsToken
Expect "LOGISTICS_OFFICER" "POST"   "/api/equipment-types" 403 @{ code = "LOGX"; name = "Logistics Type"; category = "x"; unit = "pcs" } $logisticsToken

if ($foreignPersonnel.Count -ge 1) {
    $r = Invoke-Api -Method DELETE -Path "/api/personnel/$($foreignPersonnel[0].id)" -Token $logisticsToken
    Check "LOGISTICS_OFFICER cannot delete personnel by id (403)" ($r.Status -eq 403) "HTTP $($r.Status)"
}
if ($foreignAssignment) {
    $r = Invoke-Api -Path "/api/assignments/$($foreignAssignment.id)" -Token $logisticsToken
    Check "LOGISTICS_OFFICER cannot read an assignment by id (403)" ($r.Status -eq 403) "HTTP $($r.Status)"
}

Section "Cross-role write attempts are refused"
$logisticsPurchase = Invoke-Api -Method POST -Path "/api/purchases" -Token $logisticsToken -Body @{
    baseId = $myBase; supplier = "RBAC"; purchaseDate = "2026-02-01"; items = @(@{ equipmentTypeId = $anyTypeId; quantity = 1; unitCost = 1 })
}
Check "LOGISTICS_OFFICER can create a purchase (201)" ($logisticsPurchase.Status -eq 201) "HTTP $($logisticsPurchase.Status) $($logisticsPurchase.Raw)"

$commanderForeignPurchase = Invoke-Api -Method POST -Path "/api/purchases" -Token $commanderToken -Body @{
    baseId = $otherBaseId; supplier = "RBAC"; purchaseDate = "2026-02-01"; items = @(@{ equipmentTypeId = $anyTypeId; quantity = 1; unitCost = 1 })
}
Check "BASE_COMMANDER cannot create a purchase for another base (403)" ($commanderForeignPurchase.Status -eq 403) "HTTP $($commanderForeignPurchase.Status)"

$commanderOwnPurchase = Invoke-Api -Method POST -Path "/api/purchases" -Token $commanderToken -Body @{
    baseId = $myBase; supplier = "RBAC"; purchaseDate = "2026-02-01"; items = @(@{ equipmentTypeId = $anyTypeId; quantity = 1; unitCost = 1 })
}
Check "BASE_COMMANDER can create a purchase for their own base (201)" ($commanderOwnPurchase.Status -eq 201) "HTTP $($commanderOwnPurchase.Status) $($commanderOwnPurchase.Raw)"

Section "A forged, tampered or non-canonical token is rejected"
$forged = Invoke-Api -Path "/api/inventory" -Token "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwicm9sZSI6IkFETUlOIn0.forged-signature"
Check "Forged JWT is rejected (401)" ($forged.Status -eq 401) "HTTP $($forged.Status)"

# Flip a character in the middle of the signature. Tinkering with the LAST
# character is unreliable: a 43 character base64url signature carries 2 unused
# trailing bits, so some single-character edits decode to the same bytes.
$sigStart = $adminToken.LastIndexOf('.') + 1
$sig = $adminToken.Substring($sigStart)
$mid = [int]($sig.Length / 2)
$flip = if ($sig[$mid] -eq 'A') { 'B' } else { 'A' }
$midTampered = $adminToken.Substring(0, $sigStart + $mid) + $flip + $adminToken.Substring($sigStart + $mid + 1)
$midResult = Invoke-Api -Path "/api/inventory" -Token $midTampered
Check "JWT with a modified signature byte is rejected (401)" ($midResult.Status -eq 401) "HTTP $($midResult.Status)"

# Signature malleability: every last-character variant must be rejected, not just
# the ones that happen to change the decoded bytes.
$malleable = 0
foreach ($c in 'k', 'o', 's', 'w', 'x', 'y', 'z', '0', '4', '8') {
    if ($adminToken.EndsWith($c)) { continue }
    $variant = $adminToken.Substring(0, $adminToken.Length - 1) + $c
    $r = Invoke-Api -Path "/api/inventory" -Token $variant
    if ($r.Status -eq 200) { $malleable++ }
}
Check "No non-canonical signature encoding is accepted" ($malleable -eq 0) "accepted=$malleable"

$payloadTampered = $adminToken.Substring(0, $adminToken.IndexOf('.') + 1) + "eyJzdWIiOiJhZG1pbiJ9" + $adminToken.Substring($adminToken.IndexOf('.', $adminToken.IndexOf('.') + 1))
$payloadResult = Invoke-Api -Path "/api/inventory" -Token $payloadTampered
Check "JWT with a swapped payload is rejected (401)" ($payloadResult.Status -eq 401) "HTTP $($payloadResult.Status)"

$noToken = Invoke-Api -Path "/api/inventory"
Check "A request with no token is rejected (401)" ($noToken.Status -eq 401) "HTTP $($noToken.Status)"

Write-Host ""
Write-Host ("Passed: {0}   Failed: {1}" -f $script:pass, $script:fail) -ForegroundColor White
if ($script:fail -gt 0) {
    Write-Host "`nFailures:" -ForegroundColor Red
    $script:failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    exit 1
}
Write-Host "All RBAC checks passed." -ForegroundColor Green
exit 0
