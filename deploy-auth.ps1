# deploy-auth.ps1
# Builds fermi-controls-auth and acnet-setting, then copies both JARs into
# the Phoebus product lib folder.  Run this after any change to either project.
#
# Usage:
#   .\deploy-auth.ps1              # normal incremental deploy
#   .\deploy-auth.ps1 -FullBuild   # do a full product build first (needed on fresh clone)

param(
    [switch]$FullBuild
)

$ErrorActionPreference = "Stop"

$fermiAuthDir  = "c:\java_code\fermi-controls-auth"
$phoebusDir    = "c:\myLocalRepo\phoebus-fnal"
$productLib    = "$phoebusDir\product-fnal\target\lib"

# ── Step 1: build fermi-controls-auth ────────────────────────────────────────
Write-Host "`n==> Building fermi-controls-auth..." -ForegroundColor Cyan
Push-Location $fermiAuthDir
mvn clean install -DskipTests
if ($LASTEXITCODE -ne 0) { Pop-Location; exit 1 }
Pop-Location

# ── Step 2: full product build if requested OR if target/lib doesn't exist ───
if ($FullBuild -or !(Test-Path $productLib)) {
    Write-Host "`n==> Running full Phoebus product build (this takes a while)..." -ForegroundColor Cyan
    Push-Location $phoebusDir
    mvn clean install -DskipTests
    if ($LASTEXITCODE -ne 0) { Pop-Location; exit 1 }
    Pop-Location
} else {
    # ── Step 3: incremental — just rebuild acnet-setting ─────────────────────
    Write-Host "`n==> Building acnet-setting..." -ForegroundColor Cyan
    Push-Location $phoebusDir
    mvn clean install -DskipTests -pl acnet-setting
    if ($LASTEXITCODE -ne 0) { Pop-Location; exit 1 }
    Pop-Location
}

# ── Step 4: copy both JARs into product lib ───────────────────────────────────
Write-Host "`n==> Deploying JARs to $productLib ..." -ForegroundColor Cyan

Copy-Item "$fermiAuthDir\target\fermi-controls-auth-1.0.0.jar" "$productLib\" -Force
Write-Host "  Copied fermi-controls-auth-1.0.0.jar"

Copy-Item "$phoebusDir\acnet-setting\target\acnet-setting-5.0.2-85.jar" "$productLib\" -Force
Write-Host "  Copied acnet-setting-5.0.2-85.jar"

Write-Host "`n==> Done! Restart Phoebus to pick up changes.`n" -ForegroundColor Green
