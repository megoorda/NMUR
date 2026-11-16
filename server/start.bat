@echo off
cd /d "%~dp0"
if not exist .venv (
  echo Run setup.ps1 first.
  pause
  exit /b 1
)
echo Updating yt-dlp (Instagram changes often)...
.venv\Scripts\python.exe -m pip install -q -U yt-dlp
echo Starting ReelNotes server on port 8787 ...
.venv\Scripts\python.exe -m reelnotes.app
pause
