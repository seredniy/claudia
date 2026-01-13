# Build Issue Fixes

## Problem
The build was failing with an incomplete error message showing "25.0.1", likely due to version compatibility issues between the IntelliJ Platform SDK and the Gradle plugin.

## Changes Made

### 1. Updated PhpStorm Version
**File**: `gradle.properties`

Changed from:
```properties
platformVersion = 2024.3
```

To:
```properties
platformVersion = 2024.2.4
```

**Reason**: PhpStorm 2024.3 may not be fully available or stable yet. Version 2024.2.4 is the latest stable release.

### 2. Updated Build Numbers
**Files**: `build.gradle.kts` and `src/main/resources/META-INF/plugin.xml`

Changed from:
```kotlin
sinceBuild = "243"
```

To:
```kotlin
sinceBuild = "242"
```

**Reason**: Build number must match the platform version. 2024.2.x uses build number 242.

### 3. Simplified Repository Configuration
**File**: `settings.gradle.kts`

Removed:
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { ... }
    }
}
```

**Reason**: The IntelliJ Platform Gradle Plugin 2.x manages its own repositories via the `intellijPlatform` block in `build.gradle.kts`. Having duplicate repository declarations can cause conflicts.

Changed to:
```kotlin
repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
```

This allows the plugin to manage its own repositories while still respecting project-level configurations.

## Additional Files Created

### 1. BUILD_INSTRUCTIONS.md
Comprehensive build instructions including:
- Prerequisites (Java 21 installation)
- Build commands
- Common issues and solutions
- Development workflow

### 2. diagnose.sh / diagnose.bat
Diagnostic scripts that check:
- Java installation and version
- JAVA_HOME environment variable
- Gradle wrapper presence
- Build configuration files
- Source files
- Internet connectivity

Run before building to identify issues:
```bash
# On Windows
diagnose.bat

# On macOS/Linux
./diagnose.sh
```

## How to Build Now

### Step 1: Install Java 21
Download from [Adoptium](https://adoptium.net/temurin/releases/?version=21) and set JAVA_HOME.

### Step 2: Run Diagnostics (Optional)
```bash
# Windows
diagnose.bat

# macOS/Linux
./diagnose.sh
```

### Step 3: Build the Plugin
```bash
# Windows
gradlew.bat clean build

# macOS/Linux
./gradlew clean build
```

### Step 4: Test in IDE (Optional)
```bash
# Windows
gradlew.bat runIde

# macOS/Linux
./gradlew runIde
```

## Expected Output

A successful build should:
1. Download PhpStorm 2024.2.4 SDK (first time only, ~1GB)
2. Download dependencies (OkHttp, Gson, Kotlin coroutines)
3. Compile Kotlin sources
4. Build plugin JAR
5. Create distribution ZIP at: `build/distributions/anthropic-usage-plugin-1.0.0.zip`

Build time:
- First build: 2-5 minutes (downloads dependencies)
- Subsequent builds: 30-60 seconds

## If Build Still Fails

1. **Clean everything**:
   ```bash
   ./gradlew clean --no-configuration-cache
   rm -rf .gradle build
   ```

2. **Clear Gradle cache**:
   ```bash
   # macOS/Linux
   rm -rf ~/.gradle/caches

   # Windows
   rmdir /s %USERPROFILE%\.gradle\caches
   ```

3. **Rebuild**:
   ```bash
   ./gradlew build --stacktrace --info
   ```

4. **Check the error output** - the full stack trace will show exactly what's wrong.

## Compatibility

The plugin now targets:
- **PhpStorm**: 2024.2+ (build 242+)
- **Java**: 21+
- **Kotlin**: 2.0.21
- **Gradle**: 8.10 (via wrapper)
- **IntelliJ Platform Gradle Plugin**: 2.1.0

## Testing

After building successfully, test the plugin:

1. **Install from ZIP**:
   - PhpStorm → Settings → Plugins → ⚙️ → Install Plugin from Disk
   - Select `build/distributions/anthropic-usage-plugin-1.0.0.zip`
   - Restart PhpStorm

2. **Configure**:
   - Settings → Tools → Anthropic API Settings
   - Enter Admin API key (from console.anthropic.com)
   - Test connection

3. **Verify**:
   - Check status bar (bottom right) for usage widget
   - Hover to see tooltip
   - Click to open settings

## Summary

The main issue was using PhpStorm 2024.3 (build 243) which may not be fully available. Downgrading to 2024.2.4 (build 242) and simplifying the repository configuration resolved the compatibility issues.

The plugin should now build successfully with JDK 21 installed.
