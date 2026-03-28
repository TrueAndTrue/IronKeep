@echo off
setlocal enabledelayedexpansion
echo Building IronKeep plugin...
cd /d "%~dp0plugins\ironkeep-core"
call gradlew.bat clean build
if %errorlevel% neq 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo Syncing config files...
if not exist "%~dp0server\plugins\IronKeep" mkdir "%~dp0server\plugins\IronKeep"

REM Copy all .yml files from resources to the plugin data folder automatically.
REM This picks up any new config files added in future without needing to update this script.
REM Excludes paper-plugin.yml which is plugin metadata, not a runtime config.
for %%f in ("%~dp0plugins\ironkeep-core\src\main\resources\*.yml") do (
    if /I not "%%~nxf"=="paper-plugin.yml" (
        copy /Y "%%f" "%~dp0server\plugins\IronKeep\%%~nxf" >nul
        echo   Synced: %%~nxf
    )
)

echo Starting server...
cd /d "%~dp0server"
java -Xms2G -Xmx4G -jar paper-1.21.11-127.jar --nogui
pause
