# Build Instructions

## Prerequisites

### Java Development Kit (JDK) 21

The plugin requires JDK 21 to build.

**Windows:**
Download from [Adoptium](https://adoptium.net/temurin/releases/?version=21)

**macOS:**
```bash
brew install openjdk@21
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt install openjdk-21-jdk
```

Verify installation:
```bash
java -version
```

## Building

```bash
# Clean build
./gradlew clean build

# Windows
gradlew.bat clean build
```

Output: `build/distributions/claudia-1.0.1.zip`

## Development

Run PhpStorm with the plugin:
```bash
./gradlew runIde
```

## Gradle Tasks

| Task | Description |
|------|-------------|
| `build` | Build the plugin |
| `clean` | Clean build artifacts |
| `runIde` | Run PhpStorm with plugin |
| `buildPlugin` | Build distribution ZIP |

## Installing

1. Build: `./gradlew build`
2. In PhpStorm: `Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk...`
3. Select `build/distributions/claudia-1.0.1.zip`
4. Restart PhpStorm

## Configuration

After installation:
1. `Settings` → `Tools` → `Anthropic API Settings`
2. Enter your Anthropic API key
3. Test connection

## Troubleshooting

**JAVA_HOME not set:**
```bash
export JAVA_HOME=/path/to/jdk-21
```

**Dependency issues:**
```bash
./gradlew build --refresh-dependencies
```

**Cache issues:**
```bash
./gradlew clean build --no-configuration-cache
```