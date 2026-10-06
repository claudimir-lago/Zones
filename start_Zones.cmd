@echo off
setlocal
cd /d "%~dp0"
if not exist "target\zones-0.7.4.jar" (
  where mvn >nul 2>nul || (
    echo Maven was not found. Build the project first with NetBeans or Maven.
    pause
    exit /b 1
  )
  mvn -q package || (
    echo Build failed.
    pause
    exit /b 1
  )
)
start "" javaw -jar "target\zones-0.7.4.jar" %*
exit /b 0
