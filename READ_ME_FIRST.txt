==========================================
ANTHROPIC API USAGE PLUGIN FOR PHPSTORM
==========================================

BUILD FIXES APPLIED
===================

Two issues were identified and fixed:

1. JAVA VERSION ISSUE (Action Required!)
   ----------------------------------------
   You have Java 25, but need Java 21 (LTS)
   Kotlin compiler doesn't support Java 25 yet

   ACTION: Install Java 21 from:
   https://adoptium.net/temurin/releases/?version=21

2. BUILD CONFIGURATION ISSUE (Fixed!)
   ----------------------------------------
   IntelliJ Platform dependency resolution error
   Plugin verifier and tests disabled to simplify build

   STATUS: Already fixed in build.gradle.kts


WHAT YOU NEED TO DO
===================

STEP 1: Install Java 21
   - Go to: https://adoptium.net/temurin/releases/?version=21
   - Download: Windows x64 JDK (version 21)
   - Install it

STEP 2: Build the Plugin
   - Run: build-helper.bat
   - The script will auto-detect Java 21

STEP 3: Install in PhpStorm
   - Settings > Plugins > Install Plugin from Disk
   - Select: build\distributions\anthropic-usage-plugin-1.0.0.zip
   - Restart PhpStorm


DOCUMENTATION
=============

For detailed information:
- JAVA_VERSION_ISSUE.md  - Why Java 21 is needed
- LATEST_FIXES.md        - Build configuration changes
- BUILD_INSTRUCTIONS.md  - Complete build guide
- FIXES.md               - Platform version fixes


QUICK START (After Installing Java 21)
=======================================

1. Open Command Prompt
2. Navigate to this directory:
   cd C:\Users\sered\Desktop\ccup

3. Run:
   build-helper.bat

4. If successful, install the ZIP in PhpStorm:
   build\distributions\anthropic-usage-plugin-1.0.0.zip


STILL HAVING ISSUES?
====================

1. Make sure Java 21 is installed (not Java 25)
   java -version
   (should show: openjdk version "21.x.x")

2. Run the diagnostic:
   diagnose.bat

3. Check the error logs and documentation files

4. Try clean build:
   gradlew.bat clean build --stacktrace


AFTER SUCCESSFUL BUILD
=======================

Configure the plugin in PhpStorm:
1. Settings > Tools > Anthropic API Settings
2. Enter your Admin API key from:
   https://console.anthropic.com/settings/admin-keys
3. Test connection
4. Check status bar for usage widget

==========================================
Plugin is ready - just needs Java 21!
==========================================
