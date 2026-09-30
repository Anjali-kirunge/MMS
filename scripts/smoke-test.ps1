<#
.SYNOPSIS
    End-to-end API verification for the Military Asset Management System.

.DESCRIPTION
    Exercises authentication, role-based access control, base scoping, the
    dashboard formula, every stock-affecting transaction (including rollback
    cases), user / base / equipment administration and the append-only audit
    trail. The script only reads and writes through the public REST API, so it
    is safe to run against a freshly seeded database.

    The database is intentionally left with the transactions created here, so
    re-apply database\military_asset_management.sql after running this script.

    Seeded-account passwords are never committed. Supply the three test logins
    with the -AdminPassword, -CommanderPassword and -LogisticsPassword
    parameters, or through the MAMS_ADMIN_PASSWORD, MAMS_COMMANDER_PASSWORD and
    MAMS_LOGISTICS_PASSWORD environment variables.

.PARAMETER BaseUrl
    Backend base URL. Defaults to http://localhost:8080

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1 `
        -AdminPassword "<admin-password>" `
        -CommanderPassword "<commander-password>" `
        -LogisticsPassword "<logistics-password>"
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminUsername = $(if ($env:MAMS_ADMIN_USERNAME) { $env:MAMS_ADMIN_USERNAME } else { "admin" }),
    [string]$AdminPassword = $(if ($env:MAMS_ADMIN_PASSWORD) { $env:MAMS_ADMIN_PASSWORD } else { "" }),
    [string]$CommanderUsername = "gen.alpha",
    [string]$CommanderPassword = $(if ($env:MAMS_COMMANDER_PASSWORD) { $env:MAMS_COMMANDER_PASSWORD } else { "" }),
    [string]$LogisticsUsername = "logistics",
    [string]$LogisticsPassword = $(if ($env:MAMS_LOGISTICS_PASSWORD) { $env:MAMS_LOGISTICS_PASSWORD } else { "" })
)

foreach ($required in @(
    @{ Name = "AdminPassword"; Value = $AdminPassword; Env = "MAMS_ADMIN_PASSWORD" },
    @{ Name = "CommanderPassword"; Value = $CommanderPassword; Env = "MAMS_COMMANDER_PASSWORD" },
    @{ Name = "LogisticsPassword"; Value = $LogisticsPassword; Env = "MAMS_LOGISTICS_PASSWORD" })) {
    if ([string]::IsNullOrEmpty($required.Value)) {
        throw "No $($required.Name) supplied. Pass -$($required.Name), or set the $($required.Env) environment variable before running."
    }
}

$ErrorActionPreference = "Stop"
$script:pass = 0
$script:fail = 0
$script:failures = New-Object System.Collections.Generic.List[string]

function Invoke-Api {
    param(
        [string]$Method = "GET",
        [string]$Path,
        $Body,
        [string]$Token
    )
    $headers = @{ Accept = "application/json" }
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $params = @{
        Method       = $Method
        Uri          = "$BaseUrl$Path"
        Headers      = $headers
        UseBasicParsing = $true
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
    if ($raw) {
        try { $data = $raw | ConvertFrom-Json } catch { $data = $raw }
    }
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

Write-Host "Military Asset Management System - API smoke test against $BaseUrl" -ForegroundColor White

Section "Service health"
$health = Invoke-Api -Path "/api/health"
Check "GET /api/health returns UP" ($health.Status -eq 200 -and $health.Body.status -eq "UP") $health.Raw

Section "Authentication"
$anon = Invoke-Api -Path "/api/inventory"
Check "Anonymous request is rejected with 401" ($anon.Status -eq 401) "HTTP $($anon.Status)"

$bad = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ username = $AdminUsername; password = "wrong-password" }
Check "Wrong password returns 401" ($bad.Status -eq 401) "HTTP $($bad.Status)"

$admin = Login $AdminUsername $AdminPassword
$adminToken = $admin.token
Check "admin can sign in and receives a JWT" ($admin.token.Length -gt 100) "token length=$($admin.token.Length)"
Check "admin role is reported as ADMIN" ($admin.user.role -eq "ADMIN") $admin.user.role

$commander = Login $CommanderUsername $CommanderPassword
$commanderToken = $commander.token
Check "gen.alpha can sign in as BASE_COMMANDER" ($commander.user.role -eq "BASE_COMMANDER") $commander.user.role
Check "commander is bound to a base" ($null -ne $commander.user.baseId) "baseId=$($commander.user.baseId)"

$logistics = Login $LogisticsUsername $LogisticsPassword
$logisticsToken = $logistics.token
Check "logistics can sign in as LOGISTICS_OFFICER" ($logistics.user.role -eq "LOGISTICS_OFFICER") $logistics.user.role

$me = Invoke-Api -Path "/api/auth/me" -Token $adminToken
Check "GET /api/auth/me returns the signed-in user" ($me.Status -eq 200 -and $me.Body.username -eq $AdminUsername) $me.Raw

Section "Role-based access control"
$forbidden = Invoke-Api -Path "/api/users" -Token $commanderToken
Check "BASE_COMMANDER cannot list users (403)" ($forbidden.Status -eq 403) "HTTP $($forbidden.Status)"

$forbidden = Invoke-Api -Path "/api/assignments" -Token $logisticsToken
Check "LOGISTICS_OFFICER cannot list assignments (403)" ($forbidden.Status -eq 403) "HTTP $($forbidden.Status)"

$forbidden = Invoke-Api -Path "/api/expenditures" -Token $logisticsToken
Check "LOGISTICS_OFFICER cannot list expenditures (403)" ($forbidden.Status -eq 403) "HTTP $($forbidden.Status)"

$allowed = Invoke-Api -Path "/api/purchases" -Token $logisticsToken
Check "LOGISTICS_OFFICER can list purchases" ($allowed.Status -eq 200) "HTTP $($allowed.Status)"

Section "Reference data"
$bases = Invoke-Api -Path "/api/bases" -Token $adminToken
Check "GET /api/bases returns the seeded bases" ($bases.Status -eq 200 -and $bases.Body.Count -ge 3) "HTTP $($bases.Status)"

$types = Invoke-Api -Path "/api/equipment-types" -Token $adminToken
Check "GET /api/equipment-types returns the seeded types" ($types.Status -eq 200 -and $types.Body.Count -ge 5) "HTTP $($types.Status)"

$personnel = Invoke-Api -Path "/api/personnel" -Token $adminToken
Check "GET /api/personnel returns the roster" ($personnel.Status -eq 200 -and $personnel.Body.Count -ge 1) "HTTP $($personnel.Status)"

$personnelBlocked = Invoke-Api -Path "/api/personnel" -Token $logisticsToken
Check "LOGISTICS_OFFICER cannot read the personnel roster (403)" ($personnelBlocked.Status -eq 403) "HTTP $($personnelBlocked.Status)"

Section "Base scoping"
$scoped = Invoke-Api -Path "/api/dashboard" -Token $commanderToken
$scopedBases = @($scoped.Body.rows | Select-Object -ExpandProperty baseId -Unique)
Check "BASE_COMMANDER dashboard is limited to one base" ($scopedBases.Count -eq 1 -and $scopedBases[0] -eq $commander.user.baseId) "bases=$($scopedBases -join ',')"

$crossBase = Invoke-Api -Path ("/api/dashboard?baseId=" + ($commander.user.baseId + 1)) -Token $commanderToken
Check "BASE_COMMANDER cannot read another base (403)" ($crossBase.Status -eq 403) "HTTP $($crossBase.Status)"

$otherBaseId = @($bases.Body | Where-Object { $_.id -ne $commander.user.baseId })[0].id
$crossBase = Invoke-Api -Path "/api/inventory?baseId=$otherBaseId" -Token $commanderToken
Check "BASE_COMMANDER cannot list another base's stock (403)" ($crossBase.Status -eq 403) "HTTP $($crossBase.Status)"

Section "Dashboard formula"
$dash = Invoke-Api -Path "/api/dashboard" -Token $adminToken
Check "GET /api/dashboard returns totals and rows" ($dash.Status -eq 200 -and $dash.Body.rows.Count -ge 1) "HTTP $($dash.Status)"

$formulaOk = $true
foreach ($row in $dash.Body.rows) {
    $expectedNet = $row.purchases + $row.transferIn - $row.transferOut
    $expectedClosing = $row.openingBalance + $row.purchases + $row.transferIn - $row.transferOut - $row.assigned - $row.expended
    if ($row.netMovement -ne $expectedNet) { $formulaOk = $false; $bad = "$($row.baseCode)/$($row.equipmentCode) net" }
    if ($row.closingBalance -ne $expectedClosing) { $formulaOk = $false; $bad = "$($row.baseCode)/$($row.equipmentCode) closing" }
}
Check "Net Movement = Purchases + Transfer In - Transfer Out" $formulaOk $bad
Check "Closing Balance = Opening + Purchases + In - Out - Assigned - Expended" $formulaOk $bad

$liveStock = (Invoke-Api -Path "/api/inventory?size=200" -Token $adminToken).Body.content
$closingTotal = ($dash.Body.rows | Measure-Object -Property closingBalance -Sum).Sum
$liveTotal = ($liveStock | Measure-Object -Property onHandQuantity -Sum).Sum
Check "Dashboard closing total equals live stock total" ($closingTotal -eq $liveTotal) "dashboard=$closingTotal live=$liveTotal"

Section "Purchase increases stock"
$baseId = $commander.user.baseId
# Use the equipment type that actually holds the most stock at the source base so
# the script does not depend on the order of the seeded reference data.
$stocked = (Invoke-Api -Path "/api/inventory?baseId=$baseId&size=200" -Token $adminToken).Body.content |
    Sort-Object -Property onHandQuantity -Descending |
    Select-Object -First 1
$eqId = $stocked.equipmentTypeId
Check "Found a stocked equipment type for the transaction tests" ($null -ne $eqId -and $stocked.onHandQuantity -ge 30) "$($stocked.equipmentCode) onHand=$($stocked.onHandQuantity)"
$stockBefore = (Invoke-Api -Path "/api/inventory?baseId=$baseId&equipmentTypeId=$eqId" -Token $adminToken).Body.content[0].onHandQuantity
$purchase = Invoke-Api -Method POST -Path "/api/purchases" -Token $adminToken -Body @{
    baseId         = $baseId
    supplier       = "Smoke Test Supplier"
    invoiceNo      = "SMOKE-0001"
    purchaseDate   = "2026-01-15"
    remarks        = "Automated smoke test"
    items          = @(@{ equipmentTypeId = $eqId; quantity = 25; unitCost = 12.50 })
}
Check "POST /api/purchases creates a purchase" ($purchase.Status -eq 201) "HTTP $($purchase.Status) $($purchase.Raw)"
Check "Purchase reference number is generated" ($purchase.Body.referenceNo -like "PUR-*") "$($purchase.Body.referenceNo)"

$stockAfter = (Invoke-Api -Path "/api/inventory?baseId=$baseId&equipmentTypeId=$eqId" -Token $adminToken).Body.content[0].onHandQuantity
Check "Purchase increased stock by 25" ($stockAfter -eq $stockBefore + 25) "before=$stockBefore after=$stockAfter"
Check "Purchase total cost is computed" ($purchase.Body.totalCost -eq 312.50) "$($purchase.Body.totalCost)"

Section "Transfers move stock atomically"
$destinationId = @($bases.Body | Where-Object { $_.id -ne $baseId })[0].id
$dstTypeId = $types.Body[1].id
$srcBefore = (Invoke-Api -Path "/api/inventory?baseId=$baseId&equipmentTypeId=$eqId" -Token $adminToken).Body.content[0].onHandQuantity
$dstBefore = (Invoke-Api -Path "/api/inventory?baseId=$destinationId&equipmentTypeId=$eqId" -Token $adminToken).Body.content[0].onHandQuantity

$transfer = Invoke-Api -Method POST -Path "/api/transfers" -Token $adminToken -Body @{
    sourceBaseId      = $baseId
    destinationBaseId = $destinationId
    transferDate      = "2026-01-16"
    remarks           = "Automated smoke test"
    items             = @(@{ equipmentTypeId = $eqId; quantity = 20 })
}
Check "POST /api/transfers moves stock" ($transfer.Status -eq 201) "HTTP $($transfer.Status) $($transfer.Raw)"
Check "Transfer reference number is generated" ($transfer.Body.referenceNo -like "TRF-*") "$($transfer.Body.referenceNo)"

$srcAfter = (Invoke-Api -Path "/api/inventory?baseId=$baseId&equipmentTypeId=$eqId" -Token $adminToken).Body.content[0].onHandQuantity
$dstAfter = (Invoke-Api -Path "/api/inventory?baseId=$destinationId&equipmentTypeId=$eqId" -Token $adminToken).Body.content[0].onHandQuantity
Check "Source stock decreased by 20" ($srcAfter -eq $srcBefore - 20) "before=$srcBefore after=$srcAfter"
Check "Destination stock increased by 20" ($dstAfter -eq $dstBefore + 20) "before=$dstBefore after=$dstAfter"

$badTransfer = Invoke-Api -Method POST -Path "/api/transfers" -Token $adminToken -Body @{
    sourceBaseId      = $baseId
    destinationBaseId = $destinationId
    transferDate      = "2026-01-17"
    items             = @(@{ equipmentTypeId = $eqId; quantity = 100000 })
}
Check "Transfer beyond available stock is rejected (409)" ($badTransfer.Status -eq 409) "HTTP $($badTransfer.Status)"

$srcUnchanged = (Invoke-Api -Path "/api/inventory?baseId=$baseId&equipmentTypeId=$eqId" -Token $adminToken).Body.content[0].onHandQuantity
Check "Failed transfer did not change stock (rollback)" ($srcUnchanged -eq $srcAfter) "expected=$srcAfter actual=$srcUnchanged"

$sameBase = Invoke-Api -Method POST -Path "/api/transfers" -Token $adminToken -Body @{
    sourceBaseId      = $baseId
    destinationBaseId = $baseId
    transferDate      = "2026-01-18"
    items             = @(@{ equipmentTypeId = $eqId; quantity = 1 })
}
Check "Transfer to the same base is rejected (409)" ($sameBase.Status -eq 409) "HTTP $($sameBase.Status)"

Section "Assignments and expenditures"
$ownPersonnel = @($personnel.Body | Where-Object { $_.baseId -eq $baseId })[0]
$assignment = Invoke-Api -Method POST -Path "/api/assignments" -Token $adminToken -Body @{
    baseId         = $baseId
    personnelId    = $ownPersonnel.id
    equipmentTypeId = $eqId
    quantity       = 5
    assignedDate   = "2026-01-19"
    remarks        = "Automated smoke test"
}
Check "POST /api/assignments issues assets" ($assignment.Status -eq 201) "HTTP $($assignment.Status) $($assignment.Raw)"

$afterAssign = (Invoke-Api -Path "/api/inventory?baseId=$baseId&equipmentTypeId=$eqId" -Token $adminToken).Body.content[0].onHandQuantity
Check "Assignment reduced stock by 5" ($afterAssign -eq $srcAfter - 5) "before=$srcAfter after=$afterAssign"

$expenditure = Invoke-Api -Method POST -Path "/api/expenditures" -Token $adminToken -Body @{
    baseId          = $baseId
    equipmentTypeId = $eqId
    quantity        = 3
    expendedDate    = "2026-01-20"
    reason          = "Training"
    remarks         = "Automated smoke test"
}
Check "POST /api/expenditures consumes assets" ($expenditure.Status -eq 201) "HTTP $($expenditure.Status) $($expenditure.Raw)"

$afterExpend = (Invoke-Api -Path "/api/inventory?baseId=$baseId&equipmentTypeId=$eqId" -Token $adminToken).Body.content[0].onHandQuantity
Check "Expenditure reduced stock by 3" ($afterExpend -eq $afterAssign - 3) "before=$afterAssign after=$afterExpend"

$overExpend = Invoke-Api -Method POST -Path "/api/expenditures" -Token $adminToken -Body @{
    baseId          = $baseId
    equipmentTypeId = $eqId
    quantity        = 100000
    expendedDate    = "2026-01-21"
    reason          = "Over-expenditure probe"
}
Check "Expenditure beyond available stock is rejected (409)" ($overExpend.Status -eq 409) "HTTP $($overExpend.Status)"

$afterFailedExpend = (Invoke-Api -Path "/api/inventory?baseId=$baseId&equipmentTypeId=$eqId" -Token $adminToken).Body.content[0].onHandQuantity
Check "Rejected expenditure left stock untouched" ($afterFailedExpend -eq $afterExpend) "before=$afterExpend after=$afterFailedExpend"

$overAssign = Invoke-Api -Method POST -Path "/api/assignments" -Token $adminToken -Body @{
    baseId          = $baseId
    personnelId     = $ownPersonnel.id
    equipmentTypeId = $eqId
    quantity        = 100000
    assignedDate    = "2026-01-21"
}
Check "Assignment beyond available stock is rejected (409)" ($overAssign.Status -eq 409) "HTTP $($overAssign.Status)"

$crossPersonnel = @($personnel.Body | Where-Object { $_.baseId -ne $baseId })[0]
if ($crossPersonnel) {
    $crossAssign = Invoke-Api -Method POST -Path "/api/assignments" -Token $adminToken -Body @{
        baseId          = $baseId
        personnelId     = $crossPersonnel.id
        equipmentTypeId = $eqId
        quantity        = 1
        assignedDate    = "2026-01-22"
    }
    Check "Personnel from another base cannot be issued assets (409)" ($crossAssign.Status -eq 409) "HTTP $($crossAssign.Status)"
}

$commanderScope = Invoke-Api -Path "/api/assignments?baseId=$otherBaseId" -Token $commanderToken
Check "BASE_COMMANDER cannot list other bases' assignments (403)" ($commanderScope.Status -eq 403) "HTTP $($commanderScope.Status)"

Section "Formula still holds after transactions"
$dash = Invoke-Api -Path "/api/dashboard" -Token $adminToken
$row = @($dash.Body.rows | Where-Object { $_.baseId -eq $baseId -and $_.equipmentTypeId -eq $eqId })[0]
$expectedNet = $row.purchases + $row.transferIn - $row.transferOut
$expectedClosing = $row.openingBalance + $row.purchases + $row.transferIn - $row.transferOut - $row.assigned - $row.expended
Check "Net Movement matches the row ledger" ($row.netMovement -eq $expectedNet) "net=$($row.netMovement) expected=$expectedNet"
Check "Closing Balance matches live stock" ($row.closingBalance -eq $afterExpend) "closing=$($row.closingBalance) stock=$afterExpend"

$purchases = Invoke-Api -Path "/api/dashboard/movements?type=PURCHASE&baseId=$baseId&equipmentTypeId=$eqId&from=2026-01-01&to=2026-12-31" -Token $adminToken
$outbound = Invoke-Api -Path "/api/dashboard/movements?type=TRANSFER_OUT&baseId=$baseId&equipmentTypeId=$eqId&from=2026-01-01&to=2026-12-31" -Token $adminToken
$inbound = Invoke-Api -Path "/api/dashboard/movements?type=TRANSFER_IN&baseId=$baseId&equipmentTypeId=$eqId&from=2026-01-01&to=2026-12-31" -Token $adminToken
Check "Purchase drill-down returns detail lines" ($purchases.Status -eq 200 -and $purchases.Body.lines.Count -ge 1) "HTTP $($purchases.Status) count=$($purchases.Body.lines.Count)"
Check "Transfer-out drill-down returns detail lines" ($outbound.Status -eq 200 -and $outbound.Body.lines.Count -ge 1) "HTTP $($outbound.Status) count=$($outbound.Body.lines.Count)"
Check "Transfer-in drill-down is empty for this base" ($inbound.Status -eq 200 -and $inbound.Body.lines.Count -eq 0) "HTTP $($inbound.Status) count=$($inbound.Body.lines.Count)"

$purchasedQty = ($purchases.Body.lines | Measure-Object -Property quantity -Sum).Sum
$outboundQty = ($outbound.Body.lines | Measure-Object -Property quantity -Sum).Sum
Check "Drill-down quantities reconcile with Net Movement" ($purchasedQty - $outboundQty -eq $row.netMovement) "in=$purchasedQty out=$outboundQty net=$($row.netMovement)"

$badType = Invoke-Api -Path "/api/dashboard/movements?type=NONSENSE&baseId=$baseId" -Token $adminToken
Check "Unknown drill-down type is rejected (409)" ($badType.Status -eq 409) "HTTP $($badType.Status)"

Section "Validation"
$invalid = Invoke-Api -Method POST -Path "/api/purchases" -Token $adminToken -Body @{
    baseId       = $baseId
    supplier     = ""
    purchaseDate = "2026-01-15"
    items        = @(@{ equipmentTypeId = $eqId; quantity = 0; unitCost = 1 })
}
Check "Invalid payload returns 400 with field errors" ($invalid.Status -eq 400 -and $invalid.Body.fieldErrors -ne $null) "HTTP $($invalid.Status) $($invalid.Raw)"

$badType = Invoke-Api -Method GET -Path "/api/inventory?baseId=not-a-number" -Token $adminToken
Check "Non-numeric parameter returns 400" ($badType.Status -eq 400) "HTTP $($badType.Status)"

Section "Administration"
$newUser = Invoke-Api -Method POST -Path "/api/users" -Token $adminToken -Body @{
    username = "smoke.user"
    password = "smoke123"
    fullName = "Smoke Test User"
    email    = "smoke.user@example.com"
    role     = "LOGISTICS_OFFICER"
}
Check "ADMIN can create a user" ($newUser.Status -eq 201) "HTTP $($newUser.Status) $($newUser.Raw)"
$newUserId = $newUser.Body.id

$dupe = Invoke-Api -Method POST -Path "/api/users" -Token $adminToken -Body @{
    username = "smoke.user"
    password = "smoke123"
    fullName = "Smoke Test User"
    role     = "LOGISTICS_OFFICER"
}
Check "Duplicate username returns 409" ($dupe.Status -eq 409) "HTTP $($dupe.Status)"

$updated = Invoke-Api -Method PUT -Path "/api/users/$newUserId" -Token $adminToken -Body @{
    fullName = "Smoke Test User (edited)"
    email    = "smoke.user@example.com"
    role     = "BASE_COMMANDER"
    baseId   = $baseId
    enabled  = $true
}
Check "ADMIN can update a user" ($updated.Status -eq 200 -and $updated.Body.role -eq "BASE_COMMANDER") "HTTP $($updated.Status) $($updated.Raw)"

$disabled = Invoke-Api -Method PATCH -Path "/api/users/$newUserId/enabled?enabled=false" -Token $adminToken
Check "ADMIN can disable a user" ($disabled.Status -eq 200 -and $disabled.Body.enabled -eq $false) "HTTP $($disabled.Status)"

$disabledLogin = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ username = "smoke.user"; password = "smoke123" }
Check "Disabled account cannot sign in (401)" ($disabledLogin.Status -eq 401) "HTTP $($disabledLogin.Status)"

$reset = Invoke-Api -Method PATCH -Path "/api/users/$newUserId/password" -Token $adminToken -Body @{ newPassword = "changed123" }
Check "ADMIN can reset a password (204)" ($reset.Status -eq 204) "HTTP $($reset.Status)"

$newBase = Invoke-Api -Method POST -Path "/api/bases" -Token $adminToken -Body @{
    code      = "SMK"
    name      = "Smoke Test Base"
    location  = "Test"
    commander = "Nobody"
}
Check "ADMIN can create a base" ($newBase.Status -eq 201) "HTTP $($newBase.Status) $($newBase.Raw)"

$newBaseDeleted = Invoke-Api -Method DELETE -Path "/api/bases/$($newBase.Body.id)" -Token $adminToken
Check "ADMIN can delete an unused base (204)" ($newBaseDeleted.Status -eq 204) "HTTP $($newBaseDeleted.Status)"

$usedBase = Invoke-Api -Method DELETE -Path "/api/bases/$baseId" -Token $adminToken
Check "Base with history cannot be deleted (409)" ($usedBase.Status -eq 409) "HTTP $($usedBase.Status)"

$newType = Invoke-Api -Method POST -Path "/api/equipment-types" -Token $adminToken -Body @{
    code        = "SMK-TEST"
    name        = "Smoke Test Item"
    category    = "Test"
    unit        = "pcs"
    description = "Created by smoke test"
}
Check "ADMIN can create an equipment type" ($newType.Status -eq 201) "HTTP $($newType.Status) $($newType.Raw)"

$newTypeDeleted = Invoke-Api -Method DELETE -Path "/api/equipment-types/$($newType.Body.id)" -Token $adminToken
Check "ADMIN can delete an unused equipment type (204)" ($newTypeDeleted.Status -eq 204) "HTTP $($newTypeDeleted.Status)"

$usedType = Invoke-Api -Method DELETE -Path "/api/equipment-types/$eqId" -Token $adminToken
Check "Equipment type with history cannot be deleted (409)" ($usedType.Status -eq 409) "HTTP $($usedType.Status)"

$userDeleted = Invoke-Api -Method DELETE -Path "/api/users/$newUserId" -Token $adminToken
Check "ADMIN can delete a user (204)" ($userDeleted.Status -eq 204) "HTTP $($userDeleted.Status)"

$commanderUsers = Invoke-Api -Method POST -Path "/api/users" -Token $commanderToken -Body @{
    username = "commander.attempt"
    password = "NotAccepted123"
    fullName = "Should Not Exist"
    role     = "LOGISTICS_OFFICER"
}
Check "BASE_COMMANDER cannot create users (403)" ($commanderUsers.Status -eq 403) "HTTP $($commanderUsers.Status)"

Section "Audit trail"
$audit = Invoke-Api -Path "/api/audit-logs?size=200" -Token $adminToken
Check "GET /api/audit-logs returns entries" ($audit.Status -eq 200 -and $audit.Body.content.Count -ge 5) "HTTP $($audit.Status) count=$($audit.Body.content.Count)"

$actions = @($audit.Body.content | Select-Object -ExpandProperty action -Unique)
foreach ($expected in @("LOGIN", "CREATE", "LOGIN_FAILED")) {
    Check "Audit trail contains a $expected entry" ($actions -contains $expected) ($actions -join ',')
}

$auditPut = Invoke-Api -Method PUT -Path "/api/audit-logs" -Token $adminToken -Body @{ description = "tamper" }
Check "PUT on the audit collection is rejected (405)" ($auditPut.Status -eq 405) "HTTP $($auditPut.Status)"
$auditDelete = Invoke-Api -Method DELETE -Path "/api/audit-logs" -Token $adminToken
Check "DELETE on the audit collection is rejected (405)" ($auditDelete.Status -eq 405) "HTTP $($auditDelete.Status)"

$auditScoped = Invoke-Api -Path "/api/audit-logs?size=200" -Token $commanderToken
$auditBases = @($auditScoped.Body.content | Where-Object { $null -ne $_.baseId } | Select-Object -ExpandProperty baseId -Unique)
Check "BASE_COMMANDER audit view is base scoped" (
    $auditScoped.Status -eq 200 -and (@($auditBases | Where-Object { $_ -ne $baseId }).Count -eq 0)
) "bases=$($auditBases -join ',')"

$missing = Invoke-Api -Path "/api/does-not-exist" -Token $adminToken
Check "Unknown endpoint returns 404" ($missing.Status -eq 404) "HTTP $($missing.Status)"

Write-Host ""
Write-Host ("Passed: {0}   Failed: {1}" -f $script:pass, $script:fail) -ForegroundColor White
if ($script:fail -gt 0) {
    Write-Host "`nFailures:" -ForegroundColor Red
    $script:failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    exit 1
}
Write-Host "All checks passed." -ForegroundColor Green
Write-Host "Re-apply database\military_asset_management.sql to return the database to its seeded state." -ForegroundColor Yellow
exit 0
