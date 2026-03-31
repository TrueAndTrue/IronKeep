@echo off
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

REM Copy .yml files from resources to the plugin data folder.
REM Excludes paper-plugin.yml (plugin metadata).
REM
REM Pure-config files (no runtime data): always overwrite so edits deploy immediately.
REM Runtime-data files (config.yml, kitchen.yml): seed-only — only copy if missing,
REM   so commission board frame locations and ingredient frame bindings are not wiped.
for %%f in ("%~dp0plugins\ironkeep-core\src\main\resources\*.yml") do (
    if /I not "%%~nxf"=="paper-plugin.yml" (
        if /I "%%~nxf"=="config.yml" (
            if not exist "%~dp0server\plugins\IronKeep\%%~nxf" (
                copy "%%f" "%~dp0server\plugins\IronKeep\%%~nxf" >nul
                echo   Seeded: %%~nxf
            )
        ) else if /I "%%~nxf"=="kitchen.yml" (
            if not exist "%~dp0server\plugins\IronKeep\%%~nxf" (
                copy "%%f" "%~dp0server\plugins\IronKeep\%%~nxf" >nul
                echo   Seeded: %%~nxf
            )
        ) else (
            copy "%%f" "%~dp0server\plugins\IronKeep\%%~nxf" >nul
            echo   Synced: %%~nxf
        )
    )
)

echo Starting server...
cd /d "%~dp0server"
java -Xms2G -Xmx4G -jar paper-1.21.11-127.jar --nogui
pause
