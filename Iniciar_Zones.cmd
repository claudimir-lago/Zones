@echo off
setlocal
cd /d "%~dp0"
if not exist "target\zones-0.2.0.jar" (
  echo Run Clean and Build in NetBeans first.
  pause
  exit /b 1
)
java -jar "target\zones-0.2.0.jar" %*
if errorlevel 1 pause
