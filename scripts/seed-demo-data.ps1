<#
.SYNOPSIS
    Seeds the local environment with one tenant + one owner + a building + a flat,
    with the tenant assigned to the flat. After running, the contact-owner /
    schedule-visit / lease / payments / maintenance flows all light up with real
    data instead of empty states.

.DESCRIPTION
    Hits the gateway at http://localhost:8080/rentals/v1 by default. Idempotent —
    if the demo accounts already exist (registration returns 409), the script
    logs in as them and continues with the remaining steps. Building / flat
    creation is also tolerant of duplicates (it logs and uses the existing rows
    when the matching ones are already there).

.PARAMETER Gateway
    Base URL for the API gateway. Defaults to http://localhost:8080/rentals/v1.

.PARAMETER OwnerEmail / OwnerPassword / OwnerUserName
    Credentials for the demo owner account. Sensible defaults provided.

.PARAMETER TenantEmail / TenantPassword / TenantUserName
    Credentials for the demo tenant account.

.EXAMPLE
    pwsh -File .\scripts\seed-demo-data.ps1

    Seeds with the default credentials, against the local gateway. After it's
    done you can sign in as either user from the SPA login page.

.EXAMPLE
    pwsh -File .\scripts\seed-demo-data.ps1 -Gateway "https://demo.anirudhhomes.in/rentals/v1"

    Seeds against a different environment.
#>

param(
    [string]$Gateway        = "http://localhost:8080/rentals/v1",
    [string]$OwnerEmail     = "demo.owner@anirudhhomes.in",
    [string]$OwnerUserName  = "demoowner",
    [string]$OwnerPassword  = "Owner@123",
    [string]$OwnerFirstName = "Demo",
    [string]$OwnerLastName  = "Owner",
    [string]$OwnerPhone     = "+919876500001",

    [string]$TenantEmail     = "demo.tenant@anirudhhomes.in",
    [string]$TenantUserName  = "demotenant",
    [string]$TenantPassword  = "Tenant@123",
    [string]$TenantFirstName = "Demo",
    [string]$TenantLastName  = "Tenant",
    [string]$TenantPhone     = "+919876500002",

    [string]$BuildingName    = "Sunrise Residency",
    [string]$BuildingAddress = "12 MG Road, Sector 9",
    [string]$BuildingCity    = "Bangalore",
    [string]$BuildingState   = "Karnataka",

    [string]$FlatNumber  = "B-202",
    [int]$FlatFloor      = 2,
    [int]$FlatBedrooms   = 1,
    [int]$FlatBathrooms  = 1,
    [double]$FlatAreaSqft = 650,
    [int]$FlatRent       = 16000
)

$ErrorActionPreference = "Stop"

# ---------- helpers ----------

function Step([string]$msg) {
    Write-Host ""
    Write-Host "== $msg ==" -ForegroundColor Cyan
}

function Info([string]$msg) {
    Write-Host "  $msg" -ForegroundColor DarkGray
}

function Ok([string]$msg) {
    Write-Host "  ✓ $msg" -ForegroundColor Green
}

function Warn([string]$msg) {
    Write-Host "  ! $msg" -ForegroundColor Yellow
}

# Wraps Invoke-RestMethod so we can inspect Status codes (409 / 400) without
# the script aborting via $ErrorActionPreference="Stop".
function Invoke-Json {
    param(
        [Parameter(Mandatory)] [string]$Method,
        [Parameter(Mandatory)] [string]$Url,
        [object]$Body,
        [hashtable]$Headers = @{}
    )

    $params = @{
        Method      = $Method
        Uri         = $Url
        ContentType = "application/json"
        Headers     = $Headers
    }
    if ($Body -ne $null) {
        $params.Body = ($Body | ConvertTo-Json -Depth 10)
    }

    try {
        return @{
            Ok       = $true
            Status   = 200
            Response = Invoke-RestMethod @params
        }
    }
    catch {
        $status = 0
        $message = $_.Exception.Message
        $body = $null
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($stream)
                $body = $reader.ReadToEnd()
            } catch {}
        }
        return @{
            Ok       = $false
            Status   = $status
            Message  = $message
            Body     = $body
        }
    }
}

function Register-Or-Login {
    param(
        [string]$UserName,
        [string]$Password,
        [string]$Email,
        [string]$FirstName,
        [string]$LastName,
        [string]$Phone,
        [string]$Role
    )

    Info "Trying registration as $UserName ($Role)..."
    $registerBody = @{
        userName     = $UserName
        userPassword = $Password
        userRole     = $Role
        email        = $Email
        firstName    = $FirstName
        lastName     = $LastName
        phone        = $Phone
    }
    $reg = Invoke-Json -Method POST -Url "$Gateway/auth/register" -Body $registerBody

    if ($reg.Ok) {
        Ok "Registered $UserName"
    }
    else {
        Warn "Register returned $($reg.Status). Assuming user already exists; logging in instead."
    }

    Info "Logging in as $UserName..."
    $loginBody = @{ userName = $UserName; password = $Password }
    $login = Invoke-Json -Method POST -Url "$Gateway/auth/login" -Body $loginBody
    if (-not $login.Ok) {
        throw "Login failed for $UserName : $($login.Status) $($login.Body)"
    }
    Ok "Logged in as $UserName (authUserId=$($login.Response.authUserId))"
    return $login.Response
}

# ---------- main ----------

Step "Seeding Anirudh Homes demo data via $Gateway"

# 1. Owner account
Step "Step 1/5  · Owner account"
$owner = Register-Or-Login `
    -UserName $OwnerUserName -Password $OwnerPassword -Email $OwnerEmail `
    -FirstName $OwnerFirstName -LastName $OwnerLastName -Phone $OwnerPhone `
    -Role "OWNER"
$ownerToken   = $owner.accessToken
$ownerAuthId  = $owner.authUserId
$ownerHeaders = @{ Authorization = "Bearer $ownerToken" }

# 2. Tenant account
Step "Step 2/5  · Tenant account"
$tenant = Register-Or-Login `
    -UserName $TenantUserName -Password $TenantPassword -Email $TenantEmail `
    -FirstName $TenantFirstName -LastName $TenantLastName -Phone $TenantPhone `
    -Role "TENANT"
$tenantAuthId  = $tenant.authUserId
$tenantToken   = $tenant.accessToken

# 3. Building (owned by owner)
Step "Step 3/5  · Building"
$existingBuildings = Invoke-Json -Method GET `
    -Url "$Gateway/properties/buildings/owner/$ownerAuthId" `
    -Headers $ownerHeaders
$buildingId = $null
if ($existingBuildings.Ok -and $existingBuildings.Response.Count -gt 0) {
    $matching = $existingBuildings.Response | Where-Object { $_.buildingName -eq $BuildingName } | Select-Object -First 1
    if ($matching) {
        $buildingId = $matching.buildingId
        Ok "Reusing existing building $BuildingName (buildingId=$buildingId)"
    }
}
if (-not $buildingId) {
    $buildingBody = @{
        buildingName        = $BuildingName
        ownerId             = $ownerAuthId
        buildingAddress     = $BuildingAddress
        buildingCity        = $BuildingCity
        buildingState       = $BuildingState
        buildingTotalFloors = 8
        buildingTotalFlats  = 8
        amenities           = "Lift, 24x7 Security, Power Backup, Garden"
    }
    $bldgRes = Invoke-Json -Method POST `
        -Url "$Gateway/properties/buildings/create/building" `
        -Body $buildingBody -Headers $ownerHeaders
    if (-not $bldgRes.Ok) {
        throw "Building create failed : $($bldgRes.Status) $($bldgRes.Body)"
    }
    $buildingId = $bldgRes.Response.buildingId
    Ok "Created building $BuildingName (buildingId=$buildingId)"
}

# 4. Flat in that building (tenant attached at create time)
Step "Step 4/5  · Flat"
$flatsRes = Invoke-Json -Method GET `
    -Url "$Gateway/properties/flats/building/$buildingId" `
    -Headers $ownerHeaders
$flatId = $null
if ($flatsRes.Ok -and $flatsRes.Response.Count -gt 0) {
    $matching = $flatsRes.Response | Where-Object { $_.flatNumber -eq $FlatNumber } | Select-Object -First 1
    if ($matching) {
        $flatId = $matching.id
        Ok "Reusing existing flat $FlatNumber (flatId=$flatId)"
    }
}
if (-not $flatId) {
    $flatBody = @{
        buildingId  = $buildingId
        flatNumber  = $FlatNumber
        floor       = $FlatFloor
        bedrooms    = $FlatBedrooms
        bathrooms   = $FlatBathrooms
        areaSqft    = $FlatAreaSqft
        rentAmount  = $FlatRent
    }
    $flatRes = Invoke-Json -Method POST `
        -Url "$Gateway/properties/flats/create/flat" `
        -Body $flatBody -Headers $ownerHeaders
    if (-not $flatRes.Ok) {
        throw "Flat create failed : $($flatRes.Status) $($flatRes.Body)"
    }
    $flatId = $flatRes.Response.id
    Ok "Created flat $FlatNumber (flatId=$flatId)"
}

# 5. Assign tenant to flat (if not already)
Step "Step 5/5  · Assign tenant"
$flatNow = Invoke-Json -Method GET `
    -Url "$Gateway/properties/flats/$flatId" -Headers $ownerHeaders
if ($flatNow.Ok -and $flatNow.Response.tenantId -eq $tenantAuthId) {
    Ok "Tenant already assigned to flat (tenantId=$tenantAuthId)"
}
else {
    $assignBody = @{
        tenantId       = $tenantAuthId
        leaseStartDate = (Get-Date).ToString("yyyy-MM-dd")
        leaseEndDate   = (Get-Date).AddYears(1).ToString("yyyy-MM-dd")
    }
    $assign = Invoke-Json -Method POST `
        -Url "$Gateway/properties/flats/$flatId/assign" `
        -Body $assignBody -Headers $ownerHeaders
    if (-not $assign.Ok) {
        throw "Assign failed : $($assign.Status) $($assign.Body)"
    }
    Ok "Assigned tenant ($tenantAuthId) to flat $flatId"
}

# ---------- recap ----------

Write-Host ""
Write-Host "==========================================" -ForegroundColor Green
Write-Host " Demo data seeded successfully" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
Write-Host ""
Write-Host " Owner login:" -ForegroundColor White
Write-Host "   userName : $OwnerUserName"
Write-Host "   password : $OwnerPassword"
Write-Host "   email    : $OwnerEmail"
Write-Host "   authId   : $ownerAuthId"
Write-Host ""
Write-Host " Tenant login:" -ForegroundColor White
Write-Host "   userName : $TenantUserName"
Write-Host "   password : $TenantPassword"
Write-Host "   email    : $TenantEmail"
Write-Host "   authId   : $tenantAuthId"
Write-Host ""
Write-Host " Property:" -ForegroundColor White
Write-Host "   building : $BuildingName  ($buildingId)"
Write-Host "   flat     : $FlatNumber  ($flatId)"
Write-Host "   address  : $BuildingAddress, $BuildingCity, $BuildingState"
Write-Host "   rent     : INR $FlatRent / month"
Write-Host ""
Write-Host " Try it:" -ForegroundColor White
Write-Host "   1. Sign in to the SPA as the tenant. Lease, Payments,"
Write-Host "      Maintenance and the rest of the gated features unlock."
Write-Host "   2. Open the property's public detail page; click"
Write-Host "      'Contact owner' — the owner's phone + email show up,"
Write-Host "      WhatsApp deep-link and click-to-call work."
Write-Host "   3. Sign in as the owner; the tenant card and active lease"
Write-Host "      appear under Tenants / Agreements."
Write-Host ""
