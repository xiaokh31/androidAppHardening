@echo off
setlocal
set "AH_APP_HOME=%~dp0.."
set "AH_JAVA_CHECK=%TEMP%\android-app-hardening-java-%RANDOM%-%RANDOM%.txt"
set "AH_JAVA=java"

if not defined JAVA_HOME goto :find_java
if not exist "%JAVA_HOME%\bin\java.exe" goto :find_java
set "AH_JAVA=%JAVA_HOME%\bin\java.exe"
goto :java_selected

:find_java
where java >nul 2>&1
if errorlevel 1 goto :unsupported

:java_selected
"%AH_JAVA%" -XshowSettings:properties -version >nul 2>"%AH_JAVA_CHECK%"
if errorlevel 1 goto :unsupported
findstr /L /E /C:"java.vendor = Eclipse Adoptium" "%AH_JAVA_CHECK%" >nul || goto :unsupported
findstr /L /E /C:"java.version = 17.0.19" "%AH_JAVA_CHECK%" >nul || goto :unsupported
findstr /L /E /C:"java.runtime.version = 17.0.19+10" "%AH_JAVA_CHECK%" >nul || goto :unsupported
findstr /L /E /C:"java.vm.version = 17.0.19+10" "%AH_JAVA_CHECK%" >nul || goto :unsupported
del /Q "%AH_JAVA_CHECK%" >nul 2>&1
"%AH_JAVA%" -cp "%AH_APP_HOME%\lib\android-app-hardening.jar" ah.host.cli.CliMain %*
exit /B %ERRORLEVEL%

:unsupported
del /Q "%AH_JAVA_CHECK%" >nul 2>&1
>&2 echo android-app-hardening requires Eclipse Temurin 17.0.19+10
exit /B 78
