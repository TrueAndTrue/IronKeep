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

REM === Minecraft world data ===
if exist "%SERVER%\world\playerdata\%UUID%.dat" del "%SERVER%\world\playerdata\%UUID%.dat"
if exist "%SERVER%\world\playerdata\%UUID%.dat_old" del "%SERVER%\world\playerdata\%UUID%.dat_old"
if exist "%SERVER%\world\advancements\%UUID%.json" del "%SERVER%\world\advancements\%UUID%.json"
if exist "%SERVER%\world\stats\%UUID%.json" del "%SERVER%\world\stats\%UUID%.json"

REM === IronKeep plugin data ===
REM Balances (Gold Coins + Shards)
if exist "%PLUGIN%\balances.yml" del "%PLUGIN%\balances.yml"

REM Commission state
if exist "%PLUGIN%\player-commissions.yml" del "%PLUGIN%\player-commissions.yml"
if exist "%PLUGIN%\data\commission-state.yml" del "%PLUGIN%\data\commission-state.yml"

REM Rank data
if exist "%PLUGIN%\data\player-ranks.yml" del "%PLUGIN%\data\player-ranks.yml"

REM Escape (prestige) data
if exist "%PLUGIN%\data\player-escapes.yml" del "%PLUGIN%\data\player-escapes.yml"

REM Daily quest state
if exist "%PLUGIN%\data\daily-quest.yml" del "%PLUGIN%\data\daily-quest.yml"

REM Daily commission bonus state
if exist "%PLUGIN%\data\daily-commission-bonus.yml" del "%PLUGIN%\data\daily-commission-bonus.yml"

REM First-join flags
if exist "%PLUGIN%\received-kits.yml" del "%PLUGIN%\received-kits.yml"
if exist "%PLUGIN%\warden-seen.yml" del "%PLUGIN%\warden-seen.yml"

echo Done! Full player reset complete (world data, balances, rank, escape, commissions, daily quest, first-join flags).
pause
