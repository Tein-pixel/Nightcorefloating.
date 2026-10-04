@echo off
set "GRADLE_VERSION=8.2.1"
set "CACHE_DIR=%USERPROFILE%\.gradle\nightcore-distributions"
set "ZIP_PATH=%CACHE_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
set "GRADLE_HOME=%CACHE_DIR%\gradle-%GRADLE_VERSION%\gradle-%GRADLE_VERSION%"
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
  if not exist "%ZIP_PATH%" powershell -NoProfile -Command "Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP_PATH%'"
  powershell -NoProfile -Command "Expand-Archive -Force '%ZIP_PATH%' '%CACHE_DIR%'"
)
call "%GRADLE_HOME%\bin\gradle.bat" %*
