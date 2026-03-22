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
copy /Y "%~dp0plugins\ironkeep-core\src\main\resources\config.yml"       "%~dp0server\plugins\IronKeep\config.yml"
copy /Y "%~dp0plugins\ironkeep-core\src\main\resources\commissions.yml"  "%~dp0server\plugins\IronKeep\commissions.yml"
copy /Y "%~dp0plugins\ironkeep-core\src\main\resources\starter-kit.yml"  "%~dp0server\plugins\IronKeep\starter-kit.yml"
copy /Y "%~dp0plugins\ironkeep-core\src\main\resources\ranks.yml"         "%~dp0server\plugins\IronKeep\ranks.yml"
copy /Y "%~dp0plugins\ironkeep-core\src\main\resources\escapes.yml"       "%~dp0server\plugins\IronKeep\escapes.yml"
copy /Y "%~dp0plugins\ironkeep-core\src\main\resources\daily-quests.yml"  "%~dp0server\plugins\IronKeep\daily-quests.yml"

echo Starting server...
cd /d "%~dp0server"
java -Xms2G -Xmx4G -jar paper-1.21.11-127.jar --nogui
pause
