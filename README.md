# Anthropic API Usage Plugin for PhpStorm

A PhpStorm plugin that displays your Anthropic API token usage in the IDE's status bar with a visual progress bar.

## Features

- **Real-time Usage Monitoring**: Displays current token usage in the status bar
- **Visual Progress Bar**: Color-coded progress indicator (green/orange/red based on usage)
- **Automatic Refresh**: Fetches usage data every 5 minutes (configurable)
- **Detailed Tooltips**: Hover to see breakdown by model and cache usage
- **Usage Notifications**: Optional alerts when reaching usage thresholds
- **Secure Storage**: API keys stored securely using OS-level credential managers

## Screenshots

The plugin adds a widget to the status bar showing:
```
2.5M / 10M  [=====>    ]
```

## Requirements

- PhpStorm 2024.3 or later
- Anthropic Admin API key (starts with `sk-ant-admin...`)
- Java 21 or later (for building)
- Gradle 8.10 or later (included via wrapper)

## Installation

### From Source

1. **Install Java 21**:
   - Download from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/)
   - Set `JAVA_HOME` environment variable

2. **Build the plugin**:
   ```bash
   ./gradlew build
   ```

3. **Install in PhpStorm**:
   - Open PhpStorm
   - Go to `Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk...`
   - Select `build/distributions/anthropic-usage-plugin-1.0.0.zip`
   - Restart PhpStorm

### From Marketplace (Future)
Once published, install directly from the JetBrains Marketplace.

## Configuration

### Get Your Admin API Key

1. Go to [Anthropic Console](https://console.anthropic.com/settings/admin-keys)
2. Create a new Admin API key
3. Copy the key (it starts with `sk-ant-admin...`)

**Important**: You need an *Admin API key*, not a regular API key. Only Admin keys can access usage data.

### Configure the Plugin

1. Open PhpStorm Settings: `Settings` → `Tools` → `Anthropic API Settings`
2. Enter your Admin API key
3. (Optional) Adjust settings:
   - Refresh interval (default: 5 minutes)
   - Token limit (default: 1,000,000)
   - Notification threshold (default: 90%)
4. Click "Test Connection" to verify your key works
5. Click "OK" to save

The widget will appear in the status bar (bottom right).

## Usage

### Status Bar Widget

- **Text**: Shows current usage (e.g., "2.5M / 10M")
- **Progress Bar**: Visual indicator with color coding
  - 🟢 Green: < 75% used
  - 🟠 Orange: 75-90% used
  - 🔴 Red: > 90% used
- **Click**: Opens settings dialog
- **Hover**: Shows detailed tooltip with:
  - Input/output token breakdown
  - Cache read/creation tokens
  - Per-model usage
  - Last update time

### Notifications

When you reach your configured threshold (default 90%), you'll receive a notification with:
- Current usage percentage
- Total tokens used vs. limit
- Link to open settings

## Project Structure

```
anthropic-usage-plugin/
├── src/main/kotlin/com/example/anthropic/
│   ├── api/                           # API integration
│   │   ├── AnthropicApiClient.kt     # HTTP client
│   │   ├── AnthropicApiService.kt    # High-level API service
│   │   └── models/                    # Data models
│   │       ├── UsageData.kt          # Domain model
│   │       ├── UsageReportRequest.kt # API request
│   │       └── UsageReportResponse.kt# API response
│   ├── settings/                      # Settings infrastructure
│   │   ├── AnthropicSettingsState.kt # Persistent settings
│   │   ├── AnthropicSettingsComponent.kt # UI form
│   │   └── AnthropicSettingsConfigurable.kt # Settings integration
│   ├── statusbar/                     # Status bar widget
│   │   ├── AnthropicUsageWidgetFactory.kt # Widget factory
│   │   └── AnthropicUsageWidget.kt   # Main widget implementation
│   ├── services/                      # Background services
│   │   ├── AnthropicUsageService.kt  # Usage tracking service
│   │   └── UsageDataCache.kt         # In-memory cache
│   └── utils/                         # Utilities
│       ├── AnthropicConstants.kt     # Constants
│       └── TokenFormatter.kt         # Token formatting
├── src/main/resources/
│   ├── META-INF/plugin.xml           # Plugin descriptor
│   └── messages/AnthropicBundle.properties # i18n strings
├── build.gradle.kts                   # Build configuration
├── settings.gradle.kts                # Gradle settings
├── gradle.properties                  # Plugin metadata
└── README.md                          # This file
```

## Architecture

### Key Components

1. **AnthropicApiClient**: HTTP client using OkHttp
   - Handles authentication with API key
   - Implements retry logic with exponential backoff
   - Parses JSON responses using Gson

2. **AnthropicApiService**: High-level API operations
   - Manages API client lifecycle
   - Builds usage report requests
   - Transforms responses to domain models

3. **AnthropicUsageService**: Background service
   - Application-level singleton
   - Fetches usage data every N minutes using coroutines
   - Publishes updates via IntelliJ message bus
   - Shows notifications when thresholds exceeded

4. **AnthropicUsageWidget**: Status bar UI
   - Displays usage with progress bar
   - Subscribes to usage updates
   - Shows detailed tooltip
   - Opens settings on click

5. **Settings**: Configuration management
   - Stores API key securely using PasswordSafe
   - Persists other settings in XML
   - Provides UI for configuration

### Data Flow

```
[Anthropic API]
      ↓
[AnthropicApiClient]
      ↓
[AnthropicApiService]
      ↓
[AnthropicUsageService] ← (periodic refresh)
      ↓
[Message Bus]
      ↓
[AnthropicUsageWidget] → [Status Bar]
```

## API Details

### Endpoint

```
GET https://api.anthropic.com/v1/organizations/usage_report/messages
```

### Headers

```
anthropic-version: 2023-06-01
x-api-key: sk-ant-admin...
```

### Query Parameters

- `starting_at`: ISO 8601 timestamp (start of month)
- `ending_at`: ISO 8601 timestamp (now)
- `bucket_width`: "1d" (daily buckets)
- `group_by[]`: "model" (per-model breakdown)

### Response

```json
{
  "data": [
    {
      "start_time": "2024-01-01T00:00:00Z",
      "end_time": "2024-01-02T00:00:00Z",
      "results": [
        {
          "model": "claude-3-5-sonnet-20241022",
          "input_tokens": 1000000,
          "output_tokens": 500000,
          "cache_read_tokens": 0,
          "cache_creation_tokens": 0
        }
      ]
    }
  ],
  "has_more": false,
  "next_page": null
}
```

## Development

### Build Commands

```bash
# Build the plugin
./gradlew build

# Run PhpStorm with the plugin
./gradlew runIde

# Verify plugin compatibility
./gradlew verifyPlugin

# Run tests
./gradlew test
```

### Testing

1. **Run in Development IDE**:
   ```bash
   ./gradlew runIde
   ```
   This launches a PhpStorm instance with your plugin installed.

2. **Configure API Key**:
   - Open Settings → Tools → Anthropic API Settings
   - Enter your Admin API key
   - Click "Test Connection"

3. **Verify Widget**:
   - Check status bar (bottom right)
   - Widget should show usage data
   - Hover to see tooltip
   - Click to open settings

4. **Test Refresh**:
   - Wait for automatic refresh (default: 5 minutes)
   - Or restart the IDE to trigger immediate fetch

### Debugging

- **Enable Debug Logging**:
  Add to Help → Diagnostic Tools → Debug Log Settings:
  ```
  #com.example.anthropic
  ```

- **View Logs**:
  Help → Show Log in Finder/Explorer

## Troubleshooting

### Widget Not Appearing

- **Check API Key**: Settings → Tools → Anthropic API Settings
- **Verify Key Format**: Must start with `sk-ant-admin`
- **Test Connection**: Use the "Test Connection" button
- **Check Logs**: Help → Show Log in Finder/Explorer

### Connection Failed

- **Verify Internet Connection**
- **Check API Key Validity**: Log in to Anthropic Console
- **Check Firewall/Proxy**: Ensure HTTPS connections allowed
- **Review Error Details**: Hover over error icon in status bar

### Usage Not Updating

- **Check Refresh Interval**: Settings → Tools → Anthropic API Settings
- **Verify Background Task**: Check IDE logs for errors
- **Manual Refresh**: Restart PhpStorm

### High Memory Usage

- **Increase Refresh Interval**: 5+ minutes recommended
- **Check for Memory Leaks**: Report via GitHub Issues

## Security

- **API Key Storage**: Keys stored securely using OS-level credential managers
  - macOS: Keychain
  - Windows: Credential Manager
  - Linux: Secret Service API
- **Network Security**: All API calls use HTTPS
- **No Key Logging**: API keys never logged to IDE logs

## Contributing

Contributions welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

MIT License - see LICENSE file for details

## Support

- **Issues**: Report bugs via GitHub Issues
- **Documentation**: See [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/)
- **Anthropic API**: See [Anthropic API Documentation](https://docs.anthropic.com/)

## Roadmap

Future enhancements:

- [ ] Cost tracking alongside token usage
- [ ] Historical usage charts
- [ ] Per-project usage breakdown
- [ ] Export usage reports (CSV/PDF)
- [ ] Multiple organization support
- [ ] Custom notification rules
- [ ] IDE theme integration

## Changelog

### 1.0.0 (2026-01-10)

- Initial release
- Status bar widget with token usage
- Progress bar visualization
- Settings UI for API key configuration
- Automatic refresh every 5 minutes
- Usage notifications at configurable thresholds
- Secure API key storage
- Per-model usage breakdown in tooltip

## Credits

Built with:
- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/)
- [OkHttp](https://square.github.io/okhttp/) for HTTP client
- [Gson](https://github.com/google/gson) for JSON parsing
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) for async operations

---

**Note**: This plugin requires an Anthropic Admin API key, which is different from regular API keys. Admin keys are available in the [Anthropic Console](https://console.anthropic.com/settings/admin-keys) and provide access to organization-level usage data.
