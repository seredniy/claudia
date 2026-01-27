# Session Browser — Plan

## Idea

Icon on the right IDE sidebar stripe. Click opens a panel with a list of recent Claude Code sessions for the current project. Click on a session resumes it in the built-in IDE terminal.

## Status

### Done

- [x] Data model — `SessionEntry`, `SessionsIndex`
- [x] Parser — `SessionIndexParser` (Gson-based, filters sidechains, sorts by modified desc)
- [x] Service — `SessionService` (load, resume, fork, delete, VFS watch, message bus)
- [x] Cell renderer — `SessionCellRenderer` (card-style: bold title + branch/msgs/date subtitle)
- [x] Main panel — `SessionBrowserPanel` (toolbar + JBList + context menu)
- [x] Tool window factory — `SessionBrowserToolWindowFactory` (DumbAware, right sidebar)
- [x] Custom Claudia icon for tool window stripe
- [x] Context menu with native IntelliJ ActionGroup styling
- [x] Terminal integration — waits for shell init before sending command
- [x] Delete — removes both `.jsonl` file and entry from `sessions-index.json`
- [x] Registration in `plugin.xml`
- [x] Search / filter bar — text field filtering by name, branch, or first prompt
- [x] Keyboard shortcuts — Enter = resume, Delete = delete (with confirmation)
- [x] "New Session" toolbar button — launch fresh `claude` in terminal
- [x] Tooltip on hover — shows firstPrompt, exact dates, branch, messages, session ID
- [x] Hover highlight on list items
- [x] Group by date — sections: "Today", "Yesterday", "This Week", "Older" with centered separators
- [x] NIO file watcher — replaces VFS listener for reliable external change detection
- [x] Refresh status feedback — "Refreshed — X sessions" label at bottom

### To Do — Medium Effort

- [ ] **Empty state with action** — replace "No sessions found" with a "Start Claude Code" button
- [ ] **Active session indicator** — highlight session if Claude Code is running in a terminal
- [ ] **Badge on icon** — show session count on tool window stripe icon (like Notifications)

### To Do — Future

- [ ] **Branch filter** — dropdown to filter sessions by git branch
- [ ] **Pin / Favorite** — pin important sessions to the top of the list
- [ ] **Periodic auto-refresh** — timer-based refresh in addition to NIO watcher

---

## Data Source

Sessions are stored at:
```
~/.claude/projects/{encoded-project-path}/sessions-index.json
```

Example path encoding: `/Users/smidl/Desktop/ccup` -> `-Users-smidl-Desktop-ccup`

### sessions-index.json format

```json
{
  "version": 1,
  "entries": [
    {
      "sessionId": "390b4405-1e49-46bc-b5e6-03c7fd12a9c1",
      "summary": "Analyzing Claudia Plugin & Proposing New Features",
      "firstPrompt": "проанализируй проект и предложи...",
      "messageCount": 3,
      "created": "2026-01-27T18:32:56.649Z",
      "modified": "2026-01-27T18:34:36.135Z",
      "gitBranch": "master",
      "projectPath": "/Users/smidl/Desktop/ccup",
      "isSidechain": false
    }
  ]
}
```

## UI Layout

### Right sidebar icon (tool window stripe)

```
  [ ... IDE code area ... ]   | [Claude Sessions icon]
                              | [Notifications]
                              | [AI Assistant]
```

### Tool window panel (on click)

```
┌──────────────────────────────────────────────────┐
│ Claude Sessions              [+ New] [Refresh]   │
├──────────────────────────────────────────────────┤
│ [Search sessions...]                             │
├──────────────────────────────────────────────────┤
│                                                  │
│  Today                                           │
│  ┌────────────────────────────────────────────┐  │
│  │ Analyzing Plugin & Proposing Features      │  │
│  │ master  ·  3 msgs  ·  2h ago               │  │
│  └────────────────────────────────────────────┘  │
│                                                  │
│  Yesterday                                       │
│  ┌────────────────────────────────────────────┐  │
│  │ Fix SendToClaudeAction for PhpStorm        │  │
│  │ master  ·  12 msgs  ·  yesterday           │  │
│  └────────────────────────────────────────────┘  │
│                                                  │
│  This Week                                       │
│  ┌────────────────────────────────────────────┐  │
│  │ Add status bar usage widget                │  │
│  │ feature/usage  ·  8 msgs  ·  25 Jan 10:05  │  │
│  └────────────────────────────────────────────┘  │
│                                                  │
│  ┌────────────────────────────────────────────┐  │
│  │ Initial project setup                      │  │
│  │ master  ·  2 msgs  ·  24 Jan 09:12         │  │
│  └────────────────────────────────────────────┘  │
│                                                  │
└──────────────────────────────────────────────────┘
```

### Session card detail

```
┌──────────────────────────────────────────────┐
│ Summary Title (bold, truncated)              │
│ master  ·  3 msgs  ·  2h ago                 │
└──────────────────────────────────────────────┘
```

- Bold title = `summary` from sessions-index.json
- Subtitle: git branch + message count + relative date
- Filter out `isSidechain: true` (sub-agent sessions)
- Sorted by `modified` desc (newest first)
- Tooltip: full firstPrompt + exact date + session ID

### Right-click context menu (IntelliJ ActionGroup)

```
┌───────────────────────┐
│  ▶  Resume Session    │
│ ──────────────────────│
│  ⎇  Fork Session      │
│ ──────────────────────│
│  ⧉  Copy Session ID   │
│ ──────────────────────│
│  ✕  Delete Session     │
└───────────────────────┘
```

## Actions

| Action | Behavior | Shortcut |
|--------|----------|----------|
| **Double-click / Resume** | Open new terminal tab, run `claude --resume <sessionId>` | Enter |
| **Fork** | Open new terminal tab, run `claude --resume <sessionId> --fork-session` | — |
| **Copy Session ID** | Copy UUID to clipboard | — |
| **Delete** | Remove `.jsonl` file + entry from `sessions-index.json` (with confirmation) | Delete |
| **Refresh** | Re-read `sessions-index.json` | — |
| **New Session** | Open new terminal tab, run `claude` | — |

## Architecture

```
plugin.xml
  └── <toolWindow> SessionBrowserToolWindowFactory
          │
          v
    SessionBrowserPanel (search + list + toolbar)
    ├── Search field (DocumentListener filters list)
    ├── JBList + SessionCellRenderer (card-like rendering)
    └── Context menu (IntelliJ ActionGroup)
          │
          v
    SessionService (project-level)
    ├── loadSessions() -> List<SessionEntry>
    ├── resumeSession(id) -> opens terminal with retry wait
    ├── forkSession(id) -> opens terminal
    ├── deleteSession(id) -> removes file + index entry
    └── NIO WatchService (auto-refresh on sessions-index.json change)
          │
          v
    SessionIndexParser (reads sessions-index.json via Gson)
          │
          v
    SessionEntry / SessionsIndex (data model)
```

## Files

All under `src/main/kotlin/com/example/anthropic/sessions/`.

| # | File | Status |
|---|------|--------|
| 1 | `model/SessionEntry.kt` | Done |
| 2 | `model/SessionListItem.kt` | Done |
| 3 | `parser/SessionIndexParser.kt` | Done |
| 3 | `SessionService.kt` | Done |
| 4 | `ui/SessionCellRenderer.kt` | Done |
| 5 | `ui/SessionBrowserPanel.kt` | Done |
| 6 | `ui/SessionBrowserToolWindowFactory.kt` | Done |

Other files:
ва
| File | Status |
|------|--------|
| `ClaudiaIcons.kt` | Done |
| `resources/icons/claudiaSessions.svg` | Done |
| `resources/icons/claudiaSessions_dark.svg` | Done |
| `plugin.xml` (modified) | Done |

## Key implementation details

### Path encoding
```kotlin
fun encodeProjectPath(projectPath: String): String {
    return projectPath.replace("/", "-")
}
// /Users/smidl/Desktop/ccup -> -Users-smidl-Desktop-ccup
```

### Terminal command with shell init wait
```kotlin
fun waitAndSendCommand(widget: ShellTerminalWidget, command: String, attempt: Int) {
    val starter = widget.terminalStarter
    if (starter != null) {
        starter.sendString("$command\n", false)
    } else {
        // Retry after 250ms, up to 20 attempts (5 sec max).
        Timer(250) { waitAndSendCommand(widget, command, attempt + 1) }.start()
    }
}
```

### Delete with index update
Removes both the `.jsonl` conversation file and the entry from `sessions-index.json`.

### NIO file watcher
Watches the sessions directory for changes via `java.nio.file.WatchService` (daemon thread). When Claude Code creates/modifies sessions, the list updates automatically. Replaced VFS `BulkFileListener` which didn't detect external file changes.

## Verification

1. Build: `./gradlew buildPlugin`
2. Run: `./gradlew runIde`
3. Verify Claudia icon appears on right sidebar stripe
4. Click icon — panel opens with sessions list
5. Verify sessions loaded from `~/.claude/projects/{path}/sessions-index.json`
6. Double-click session — new terminal tab opens with `claude --resume <id>`
7. Right-click — native context menu with Resume / Fork / Copy ID / Delete
8. Delete — confirmation dialog, file + index entry removed, list refreshed
9. Fork — terminal opens with `claude --resume <id> --fork-session`
