# Claudia

PhpStorm plugin for Claude Code users.

## Features

### Send to Claude Code
Right-click any file or folder in Project View → **"Send to Claude"** — instantly adds `@path/to/file` to terminal, ready for Claude Code context.

### API Usage Monitor
Status bar widget showing Anthropic API token usage with visual progress bar.

- Color-coded: 🟢 < 75% | 🟠 75-90% | 🔴 > 90%
- Hover for detailed breakdown by model
- Auto-refresh every 5 minutes

## Installation

1. Build: `./gradlew build`
2. PhpStorm: `Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk...`
3. Select `build/distributions/claudia-1.0.1.zip`

## Configuration

`Settings` → `Tools` → `Anthropic API Settings`

- Enter your [Admin API key](https://console.anthropic.com/settings/admin-keys) (starts with `sk-ant-admin...`)
- Test connection

## Requirements

- PhpStorm 2024.2+
- JDK 21 (for building)

## License

MIT
