@echo off
REM Diagnostic script for Anthropic Usage Plugin build

echo ==========================================
echo Anthropic Usage Plugin - Build Diagnostics
echo ==========================================
echo.

REM Check Java
echo 1. Checking Java installation...
where java >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    java -version 2>&1 | findstr /C:"version"
    echo    [OK] Java found
) else (
    echo    [ERROR] Java NOT found in PATH
    echo    Please install JDK 21 from https://adoptium.net/
    exit /b 1
)
echo.

REM Check JAVA_HOME
echo 2. Checking JAVA_HOME...
if defined JAVA_HOME (
    echo    JAVA_HOME=%JAVA_HOME%
    echo    [OK] JAVA_HOME is set
) else (
    echo    [WARNING] JAVA_HOME is not set
    echo    Consider setting it in System Environment Variables
)
echo.

REM Check Gradle wrapper
echo 3. Checking Gradle wrapper...
if exist "gradlew.bat" (
    echo    [OK] gradlew.bat found
) else (
    echo    [ERROR] gradlew.bat NOT found
    exit /b 1
)
echo.

REM Check build files
echo 4. Checking build configuration...
if exist "build.gradle.kts" (
    echo    [OK] build.gradle.kts found
) else (
    echo    [ERROR] build.gradle.kts NOT found
    exit /b 1
)

if exist "settings.gradle.kts" (
    echo    [OK] settings.gradle.kts found
) else (
    echo    [ERROR] settings.gradle.kts NOT found
    exit /b 1
)

if exist "gradle.properties" (
    echo    [OK] gradle.properties found
    for /f "tokens=2 delims==" %%a in ('findstr "platformVersion" gradle.properties') do (
        echo    Platform version:%%a
    )
) else (
    echo    [ERROR] gradle.properties NOT found
    exit /b 1
)
echo.

REM Check source files
echo 5. Checking source files...
if exist "src\main\kotlin" (
    dir /s /b "src\main\kotlin\*.kt" 2>nul | find /c ".kt" > temp_count.txt
    set /p COUNT=<temp_count.txt
    del temp_count.txt
    echo    [OK] Found Kotlin source files
) else (
    echo    [ERROR] No Kotlin source directory found
    exit /b 1
)
echo.

REM Check internet
echo 6. Checking internet connectivity...
ping -n 1 google.com >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo    [OK] Internet connection available
) else (
    echo    [WARNING] Cannot reach internet (may affect dependency download)
)
echo.

echo ==========================================
echo Diagnostics Complete!
echo ==========================================
echo.
echo Everything looks good! Try building with:
echo   gradlew.bat clean build
echo.
echo Or run in development IDE with:
echo   gradlew.bat runIde
echo.

pause
