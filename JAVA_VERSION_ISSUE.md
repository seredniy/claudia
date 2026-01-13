# Java Version Compatibility Issue

## The Problem

Your build is failing with this error:
```
java.lang.IllegalArgumentException: 25.0.1
at org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.parse(JavaVersion.java:305)
```

**Root Cause**: You have Java 25 installed (`C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot`), but the Kotlin compiler used by this project (version 2.0.21) doesn't support Java 25 yet. It only recognizes Java versions up to 22 or 23.

Java 25 was released in October 2025 and is very new, so many build tools haven't added support for it yet.

## The Solution

You need to install JDK 21, which is the Long-Term Support (LTS) version and what this project targets.

### Step 1: Download JDK 21

Go to [Adoptium](https://adoptium.net/temurin/releases/?version=21) and download:
- **Version**: Java 21 (LTS)
- **Operating System**: Windows
- **Architecture**: x64
- **Package Type**: JDK
- **Image Type**: JRE or JDK (choose JDK)

Direct link: https://adoptium.net/temurin/releases/?version=21

### Step 2: Install JDK 21

1. Run the installer (`.msi` file)
2. Follow the installation wizard
3. **Important**: Note the installation path, it will be something like:
   ```
   C:\Program Files\Eclipse Adoptium\jdk-21.0.X.Y-hotspot
   ```

### Step 3: Update Build Helper Script

Edit `build-helper.bat` and change this line:
```batch
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot"
```

To:
```batch
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.X.Y-hotspot"
```

Replace `21.0.X.Y` with your actual Java 21 version number.

### Step 4: Build Again

```cmd
build-helper.bat
```

The build should now succeed!

## Alternative: Set System JAVA_HOME

Instead of using the build helper script, you can set JAVA_HOME permanently in Windows:

1. Open "Environment Variables":
   - Press `Win + R`
   - Type `sysdm.cpl` and press Enter
   - Click "Advanced" tab
   - Click "Environment Variables"

2. Under "System variables", click "New":
   - Variable name: `JAVA_HOME`
   - Variable value: `C:\Program Files\Eclipse Adoptium\jdk-21.0.X.Y-hotspot`

3. Find "Path" in System variables, click "Edit":
   - Add new entry: `%JAVA_HOME%\bin`
   - Move it to the top of the list

4. Click OK to save everything

5. Open a NEW command prompt and verify:
   ```cmd
   java -version
   ```

   Should show Java 21, not Java 25.

6. Build the project:
   ```cmd
   cd C:\Users\sered\Desktop\ccup
   gradlew.bat clean build
   ```

## Why Not Just Upgrade Kotlin?

The latest Kotlin version (2.0.21) still doesn't fully support Java 25. Support for newer Java versions typically lags behind by several months. Java 21 is the recommended LTS version for stability and compatibility.

## Can I Keep Both Java 21 and Java 25?

Yes! You can keep both installed:
- Java 25 for other projects that support it
- Java 21 for this project

Just make sure `JAVA_HOME` points to Java 21 when building this plugin, or use the build-helper.bat script which sets it temporarily.

## Verification

After installing Java 21 and setting it up, verify it works:

```cmd
# Check Java version
java -version
# Should show: openjdk version "21.0.X"

# Check JAVA_HOME
echo %JAVA_HOME%
# Should show: C:\Program Files\Eclipse Adoptium\jdk-21.0.X.Y-hotspot

# Try building
cd C:\Users\sered\Desktop\ccup
gradlew.bat clean build
```

## Summary

| Issue | Java 25 not supported by Kotlin 2.0.21 |
|-------|----------------------------------------|
| **Solution** | Install Java 21 LTS |
| **Download** | https://adoptium.net/temurin/releases/?version=21 |
| **Update** | Set JAVA_HOME to Java 21 path |
| **Verify** | `java -version` should show 21.x.x |
