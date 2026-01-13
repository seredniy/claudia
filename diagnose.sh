#!/bin/bash
# Diagnostic script for Anthropic Usage Plugin build

echo "=========================================="
echo "Anthropic Usage Plugin - Build Diagnostics"
echo "=========================================="
echo ""

# Check Java
echo "1. Checking Java installation..."
if command -v java &> /dev/null; then
    java -version 2>&1 | head -1
    echo "   ✓ Java found"
else
    echo "   ✗ Java NOT found in PATH"
    echo "   Please install JDK 21 from https://adoptium.net/"
    exit 1
fi
echo ""

# Check JAVA_HOME
echo "2. Checking JAVA_HOME..."
if [ -n "$JAVA_HOME" ]; then
    echo "   JAVA_HOME=$JAVA_HOME"
    echo "   ✓ JAVA_HOME is set"
else
    echo "   ⚠ JAVA_HOME is not set (may cause issues)"
    echo "   Consider setting it in your shell profile"
fi
echo ""

# Check Java version
echo "3. Checking Java version..."
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -ge 21 ]; then
    echo "   ✓ Java version $JAVA_VERSION is compatible (need 21+)"
else
    echo "   ✗ Java version $JAVA_VERSION is too old (need 21+)"
    echo "   Please install JDK 21 from https://adoptium.net/"
    exit 1
fi
echo ""

# Check Gradle wrapper
echo "4. Checking Gradle wrapper..."
if [ -f "./gradlew" ]; then
    echo "   ✓ gradlew found"
    if [ -x "./gradlew" ]; then
        echo "   ✓ gradlew is executable"
    else
        echo "   ⚠ gradlew is not executable, fixing..."
        chmod +x ./gradlew
    fi
else
    echo "   ✗ gradlew NOT found"
    exit 1
fi
echo ""

# Check build files
echo "5. Checking build configuration..."
if [ -f "build.gradle.kts" ]; then
    echo "   ✓ build.gradle.kts found"
else
    echo "   ✗ build.gradle.kts NOT found"
    exit 1
fi

if [ -f "settings.gradle.kts" ]; then
    echo "   ✓ settings.gradle.kts found"
else
    echo "   ✗ settings.gradle.kts NOT found"
    exit 1
fi

if [ -f "gradle.properties" ]; then
    echo "   ✓ gradle.properties found"
    PLATFORM_VERSION=$(grep "platformVersion" gradle.properties | cut -d'=' -f2 | tr -d ' ')
    echo "   Platform version: $PLATFORM_VERSION"
else
    echo "   ✗ gradle.properties NOT found"
    exit 1
fi
echo ""

# Check source files
echo "6. Checking source files..."
SOURCE_FILES=$(find src/main/kotlin -name "*.kt" 2>/dev/null | wc -l)
if [ "$SOURCE_FILES" -gt 0 ]; then
    echo "   ✓ Found $SOURCE_FILES Kotlin source files"
else
    echo "   ✗ No Kotlin source files found"
    exit 1
fi
echo ""

# Check internet connection
echo "7. Checking internet connectivity..."
if ping -c 1 google.com &> /dev/null || ping -c 1 8.8.8.8 &> /dev/null; then
    echo "   ✓ Internet connection available"
else
    echo "   ⚠ Cannot reach internet (may affect dependency download)"
fi
echo ""

echo "=========================================="
echo "Diagnostics Complete!"
echo "=========================================="
echo ""
echo "Everything looks good! Try building with:"
echo "  ./gradlew clean build"
echo ""
echo "Or run in development IDE with:"
echo "  ./gradlew runIde"
echo ""
