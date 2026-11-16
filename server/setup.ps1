# One-time setup for the ReelNotes server on Windows.
# Run in PowerShell from this folder:  powershell -ExecutionPolicy Bypass -File setup.ps1
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Have($cmd) { return [bool](Get-Command $cmd -ErrorAction SilentlyContinue) }

# "python" can be a Microsoft Store placeholder, so check that it really runs.
$pyOk = $false
try { $pyOk = ((& python --version 2>&1) -match "Python 3\.(1[0-9])") } catch {}
if (-not $pyOk) {
    Write-Host "Installing Python 3.12..." -ForegroundColor Cyan
    winget install -e --id Python.Python.3.12 --accept-source-agreements --accept-package-agreements
    Write-Host "Python installed. Close this window, open a NEW PowerShell and run setup.ps1 again." -ForegroundColor Yellow
    exit 0
}
if (-not (Have "ffmpeg")) {
    Write-Host "Installing ffmpeg..." -ForegroundColor Cyan
    winget install -e --id Gyan.FFmpeg --accept-source-agreements --accept-package-agreements
}

if (-not (Test-Path ".venv")) { python -m venv .venv }
& .\.venv\Scripts\python.exe -m pip install --upgrade pip
& .\.venv\Scripts\python.exe -m pip install -r requirements.txt

if (-not (Test-Path ".env")) {
    Copy-Item .env.example .env
    $token = -join ((48..57) + (97..122) | Get-Random -Count 32 | ForEach-Object { [char]$_ })
    (Get-Content .env) -replace "change-me-to-a-long-random-string", $token | Set-Content .env
    Write-Host "Created .env with a random token: $token" -ForegroundColor Green
    Write-Host "Open server\.env in Notepad and paste your GEMINI_API_KEY." -ForegroundColor Yellow
}

# Allow the phone (over Tailscale) to reach the server.
try {
    if (-not (Get-NetFirewallRule -DisplayName "ReelNotes server" -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -DisplayName "ReelNotes server" -Direction Inbound -Protocol TCP -LocalPort 8787 -Action Allow | Out-Null
        Write-Host "Firewall rule added for port 8787." -ForegroundColor Green
    }
} catch {
    Write-Host "Could not add firewall rule (run PowerShell as Administrator, or allow Python when Windows asks)." -ForegroundColor Yellow
}

Write-Host "`nSetup done. Start the server with start.bat" -ForegroundColor Green
