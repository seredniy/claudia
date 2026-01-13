@echo off
REM ==========================================
REM Anthropic Usage Plugin - Build Script
REM ==========================================
REM
REM IMPORTANT: This project requires Java 21 (LTS)
REM If you have Java 25 installed, please install Java 21:
REM https://adoptium.net/temurin/releases/?version=21
REM

REM Try to find Java 21 first
if exist "C:\Program Files\Eclipse Adoptium\jdk-21*" (
    for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do set "JAVA_HOME=%%i"
    goto :found_java
)

REM Fall back to Java 25 (will likely fail)
if exist "C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot" (
    echo.
    echo ==========================================
    echo WARNING: Java 25 detected!
    echo ==========================================
    echo.
    echo This project requires Java 21 (LTS) to build.
    echo Java 25 is not supported by Kotlin 2.0.21 yet.
    echo.
    echo Please install Java 21 from:
    echo https://adoptium.net/temurin/releases/?version=21
    echo.
    echo See JAVA_VERSION_ISSUE.md for detailed instructions.
    echo.
    echo Press Ctrl+C to cancel, or any key to try anyway (will likely fail)...
    pause >nul

    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot"
    goto :found_java
)

REM No Java found
echo.
echo [ERROR] Java not found!
echo.
echo Please install Java 21 (LTS) from:
echo https://adoptium.net/temurin/releases/?version=21
echo.
exit /b 1

:found_java
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ==========================================
echo Building Anthropic Usage Plugin
echo ==========================================
echo.
echo JAVA_HOME: %JAVA_HOME%
echo.

"%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr "version"
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java not found at %JAVA_HOME%
    exit /b 1
)

echo.

REM Check if this is Java 21
for /f "tokens=2 delims=." %%a in ('"%JAVA_HOME%\bin\java.exe" -version 2^>^&1 ^| findstr "version"') do set JAVA_MAJOR=%%a

if "%JAVA_MAJOR%"=="21" (
    echo [OK] Java 21 detected - compatible!
) else (
    echo [WARNING] Java %JAVA_MAJOR% detected
    if "%JAVA_MAJOR%"=="25" (
        echo [ERROR] Java 25 is not supported by this project!
        echo Please install Java 21 from:
        echo https://adoptium.net/temurin/releases/?version=21
        echo.
        echo See JAVA_VERSION_ISSUE.md for instructions.
        echo.
        exit /b 1
    ) else (
        echo [WARNING] This project targets Java 21. Build may fail.
    )
)

echo.
echo Starting build...
echo.

gradlew.bat clean build

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ==========================================
    echo Build Successful!
    echo ==========================================
    echo.
    echo Plugin location: build\distributions\anthropic-usage-plugin-1.0.0.zip
    echo.
    echo Next steps:
    echo 1. Install in PhpStorm: Settings ^> Plugins ^> Install Plugin from Disk
    echo 2. Select the ZIP file above
    echo 3. Restart PhpStorm
    echo 4. Configure: Settings ^> Tools ^> Anthropic API Settings
    echo.
) else (
    echo.
    echo ==========================================
    echo Build Failed!
    echo ==========================================
    echo.
    if "%JAVA_MAJOR%"=="25" (
        echo The build failed because Java 25 is not supported.
        echo Please install Java 21 and try again.
        echo.
        echo See JAVA_VERSION_ISSUE.md for detailed instructions.
    ) else (
        echo Check the error messages above for details.
        echo.
        echo For help, see:
        echo - BUILD_INSTRUCTIONS.md
        echo - FIXES.md
        echo - JAVA_VERSION_ISSUE.md
    )
    echo.
)

pause
