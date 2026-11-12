@echo off
setlocal
set GRADLE_VERSION=8.13
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set WRAPPER_DIR=%GRADLE_USER_HOME%\geo-videos-wrapper
set GRADLE_HOME_DIR=%WRAPPER_DIR%\gradle-%GRADLE_VERSION%
set GRADLE_BIN=%GRADLE_HOME_DIR%\bin\gradle.bat
set ARCHIVE_FILE=%WRAPPER_DIR%\gradle-%GRADLE_VERSION%-bin.zip

if not exist "%GRADLE_BIN%" (
  if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
  if not exist "%ARCHIVE_FILE%" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ARCHIVE_FILE%'"
    if errorlevel 1 exit /b 1
  )
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ARCHIVE_FILE%' -DestinationPath '%WRAPPER_DIR%' -Force"
  if errorlevel 1 exit /b 1
)

call "%GRADLE_BIN%" %*
endlocal
