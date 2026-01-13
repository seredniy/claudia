# Build Instructions

## Prerequisites

### 1. Install Java Development Kit (JDK) 21

The plugin requires JDK 21 or later to build and run.

#### Windows
Download from [Adoptium](https://adoptium.net/temurin/releases/?version=21) or [Oracle](https://www.oracle.com/java/technologies/downloads/#java21)

After installation, set JAVA_HOME:
```cmd
setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-21.0.1.12-hotspot"
```

Or add to System Environment Variables via Control Panel.

#### macOS
```bash
brew install openjdk@21
echo 'export PATH="/usr/local/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

#### Linux (Ubuntu/Debian)
```bash
sudo apt update
sudo apt install openjdk-21-jdk
```

### 2. Verify Java Installation

```bash
java -version
```

Should output something like:
```
openjdk version "21.0.1" 2023-10-17
```

## Building the Plugin

### Clean Build (Recommended First Time)

```bash
cd /mnt/c/Users/sered/Desktop/ccup

# On Windows
gradlew.bat clean build

# On macOS/Linux
./gradlew clean build
```

### Regular Build

```bash
# On Windows
gradlew.bat build

# On macOS/Linux
./gradlew build
```

The built plugin will be at: `build/distributions/anthropic-usage-plugin-1.0.0.zip`

## Running in Development

To test the plugin in a PhpStorm instance:

```bash
# On Windows
gradlew.bat runIde

# On macOS/Linux
./gradlew runIde
```

This will:
1. Download PhpStorm 2024.2.4 (if not cached)
2. Install your plugin
3. Launch PhpStorm with the plugin enabled

## Common Build Issues

### Issue 1: "JAVA_HOME is not set"

**Solution**: Install JDK 21 and set JAVA_HOME environment variable (see Prerequisites above)

### Issue 2: "Could not resolve dependencies"

**Solution**: Check your internet connection and try again. Gradle needs to download dependencies.

```bash
./gradlew build --refresh-dependencies
```

### Issue 3: "Incompatible version" or "25.0.1" error

**Solution**: The build files have been updated to use PhpStorm 2024.2.4. Make sure you have the latest version of the files:

- `gradle.properties` should have `platformVersion = 2024.2.4`
- `build.gradle.kts` should have `sinceBuild = "242"`
- `plugin.xml` should have `since-build="242"`

### Issue 4: "Build cache" issues

**Solution**: Clean the cache and rebuild:

```bash
./gradlew clean --no-configuration-cache
./gradlew build --no-configuration-cache
```

### Issue 5: Gradle version mismatch

**Solution**: Use the wrapper (don't install Gradle separately):

```bash
# This uses the included Gradle wrapper at the correct version
./gradlew build
```

## Gradle Tasks

### Build Tasks
- `./gradlew build` - Build the plugin
- `./gradlew clean` - Clean build artifacts
- `./gradlew assemble` - Assemble the plugin without tests

### Development Tasks
- `./gradlew runIde` - Run PhpStorm with the plugin
- `./gradlew buildPlugin` - Build the plugin distribution ZIP
- `./gradlew verifyPlugin` - Verify plugin compatibility

### Testing Tasks
- `./gradlew test` - Run unit tests
- `./gradlew check` - Run all checks

### Debugging Tasks
- `./gradlew build --info` - Build with detailed logging
- `./gradlew build --debug` - Build with debug logging
- `./gradlew build --stacktrace` - Show full stack traces on errors

## Installing the Built Plugin

1. Build the plugin: `./gradlew build`
2. Locate the ZIP file: `build/distributions/anthropic-usage-plugin-1.0.0.zip`
3. In PhpStorm:
   - Go to `Settings` → `Plugins`
   - Click ⚙️ (gear icon) → `Install Plugin from Disk...`
   - Select the ZIP file
   - Restart PhpStorm

## Development Workflow

### Initial Setup
```bash
# Clone/create project
cd ccup

# Build to verify everything works
./gradlew build

# Run in dev IDE
./gradlew runIde
```

### Making Changes
```bash
# After code changes, rebuild
./gradlew build

# Test in IDE
./gradlew runIde

# Or just build the plugin ZIP
./gradlew buildPlugin
```

### Before Committing
```bash
# Run all checks
./gradlew check

# Verify plugin compatibility
./gradlew verifyPlugin

# Build final distribution
./gradlew buildPlugin
```

## Troubleshooting Gradle

### Clear Gradle Cache
```bash
# On macOS/Linux
rm -rf ~/.gradle/caches
rm -rf ~/.gradle/wrapper

# On Windows
rmdir /s %USERPROFILE%\.gradle\caches
rmdir /s %USERPROFILE%\.gradle\wrapper
```

### Update Gradle Wrapper
If you need to update to a different Gradle version:
```bash
gradle wrapper --gradle-version 8.10
```

### Use Different JDK
```bash
# Specify Java home explicitly
./gradlew build -Dorg.gradle.java.home=/path/to/jdk-21
```

## Next Steps

After successful build:

1. **Install the plugin** (see above)
2. **Configure API key**:
   - Open PhpStorm
   - Go to `Settings` → `Tools` → `Anthropic API Settings`
   - Enter your Anthropic Admin API key
   - Test the connection
3. **Check status bar** for the usage widget

## Getting Help

If you encounter issues:

1. Check Java version: `java -version`
2. Try clean build: `./gradlew clean build --stacktrace`
3. Check build files match the versions above
4. Review error logs in detail

Still stuck? The full error output with `--stacktrace` will help diagnose the issue.
