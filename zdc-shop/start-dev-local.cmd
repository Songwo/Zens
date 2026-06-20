@echo off
setlocal
cd /d "%~dp0"
set NEXT_TELEMETRY_DISABLED=1
echo Starting Zens points mall at http://127.0.0.1:3000
node node_modules\next\dist\bin\next dev -H 127.0.0.1 -p 3000
pause
