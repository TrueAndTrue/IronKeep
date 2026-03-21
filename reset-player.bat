@echo off
setlocal

set "SERVER=%~dp0server"
set "PLUGIN=%SERVER%\plugins\IronKeep"

if "%~1"=="" (
    echo Usage: reset-player.bat ^<uuid^>
    echo.
    echo Known players:
    type "%SERVER%\usercache.json"
    echo.
    pause
    exit /b 1
)

set "UUID=%~1"

echo Resetting player %UUID%...

if exist "%SERVER%\world\playerdata\%UUID%.dat" del "%SERVER%\world\playerdata\%UUID%.dat"
if exist "%SERVER%\world\playerdata\%UUID%.dat_old" del "%SERVER%\world\playerdata\%UUID%.dat_old"
if exist "%SERVER%\world\advancements\%UUID%.json" del "%SERVER%\world\advancements\%UUID%.json"
if exist "%SERVER%\world\stats\%UUID%.json" del "%SERVER%\world\stats\%UUID%.json"
if exist "%PLUGIN%\received-kits.yml" del "%PLUGIN%\received-kits.yml"
if exist "%PLUGIN%\warden-seen.yml" del "%PLUGIN%\warden-seen.yml"
if exist "%PLUGIN%\balances.yml" del "%PLUGIN%\balances.yml"
if exist "%PLUGIN%\commissions.yml" del "%PLUGIN%\commissions.yml"

echo Done! Player data and plugin data reset.
pause
