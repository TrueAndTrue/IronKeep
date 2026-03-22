@echo off
echo Building IronKeep plugin...
cd /d "%~dp0plugins\ironkeep-core"
call gradlew.bat clean build
if %errorlevel% neq 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo Copying config...
copy /Y "%~dp0plugins\ironkeep-core\src\main\resources\config.yml" "%~dp0server\plugins\IronKeep\config.yml"
copy /Y "%~dp0plugins\ironkeep-core\src\main\resources\commissions.yml" "%~dp0server\plugins\IronKeep\commissions.yml"

echo Starting server...
cd /d "%~dp0server"
java -Xms2G -Xmx4G -jar paper-1.21.11-127.jar --nogui
pause
