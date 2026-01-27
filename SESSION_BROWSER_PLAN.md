# Session Browser — Plan

## Idea

Icon on the right IDE sidebar stripe. Click opens a panel with a list of recent Claude Code sessions for the current project. Click on a session resumes it in the built-in IDE terminal.

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
  [ ... IDE code area ... ]   │ [Claude Sessions icon]
                              │ [Notifications]
                              │ [AI Assistant]
```

### Tool window panel (on click)

```
┌──────────────────────────────────────────────────┐
│ Claude Sessions                      [Refresh]   │
├──────────────────────────────────────────────────┤
│                                                  │
│  ┌────────────────────────────────────────────┐  │
│  │ Analyzing Plugin & Proposing Features      │  │
│  │ master  ·  3 msgs  ·  27 Jan 18:34        │  │
│  └────────────────────────────────────────────┘  │
│                                                  │
│  ┌────────────────────────────────────────────┐  │
│  │ Fix SendToClaudeAction for PhpStorm        │  │
│  │ master  ·  12 msgs  ·  26 Jan 15:20       │  │
│  └────────────────────────────────────────────┘  │
│                                                  │
│  ┌────────────────────────────────────────────┐  │
│  │ Add status bar usage widget                │  │
│  │ feature/usage  ·  8 msgs  ·  25 Jan 10:05 │  │
│  └────────────────────────────────────────────┘  │
│                                                  │
│  ┌────────────────────────────────────────────┐  │
│  │ Initial project setup                      │  │
│  │ master  ·  2 msgs  ·  24 Jan 09:12        │  │
│  └────────────────────────────────────────────┘  │
│                                                  │
│           (no more sessions)                     │
└──────────────────────────────────────────────────┘
```

### Session card detail

```
┌──────────────────────────────────────────────┐
│ Summary Title (bold, truncated)              │
│ [branch-icon] master  ·  3 msgs  ·  27 Jan  │
└──────────────────────────────────────────────┘
```

- Bold title = `summary` from sessions-index.json
- Subtitle: git branch + message count + relative date
- Filter out `isSidechain: true` (sub-agent sessions)
- Sorted by `modified` desc (newest first)

### Right-click context menu

```
┌─────────────────────┐
│ Resume Session      │
│ Fork Session        │
│ ─────────────────── │
│ Copy Session ID     │
│ ─────────────────── │
│ Delete Session      │
└─────────────────────┘
```

## Actions

| Action | Behavior |
|--------|----------|
| **Click / Resume** | Open new terminal tab, run `claude --resume <sessionId>` |
| **Fork** | Open new terminal tab, run `claude --resume <sessionId> --fork-session` |
| **Copy Session ID** | Copy UUID to clipboard |
| **Delete** | Delete the `.jsonl` file + remove entry from `sessions-index.json` (with confirmation dialog) |
| **Refresh** | Re-read `sessions-index.json` |

## Architecture

```
plugin.xml
  └── <toolWindow> SessionBrowserToolWindowFactory
          │
          v
    SessionBrowserPanel (list + toolbar)
    ├── SessionListPanel (JBList with custom cell renderer)
    ├── SessionCellRenderer (card-like rendering)
    └── Context menu actions
          │
          v
    SessionService (project-level)
    ├── loadSessions() -> List<SessionEntry>
    ├── resumeSession(id) -> opens terminal
    ├── forkSession(id) -> opens terminal
    ├── deleteSession(id)
    └── VFS listener (auto-refresh)
          │
          v
    SessionIndexParser (reads sessions-index.json)
          │
          v
    SessionEntry (data model)
```

## New files (8)

All under `src/main/kotlin/com/example/anthropic/sessions/`.

| # | File | Purpose |
|---|------|---------|
| 1 | `model/SessionEntry.kt` | Data class: sessionId, summary, messageCount, created, modified, gitBranch, isSidechain |
| 2 | `parser/SessionIndexParser.kt` | Read and parse `sessions-index.json` using Gson |
| 3 | `SessionService.kt` | Project service: load sessions, resolve paths, resume/fork/delete, VFS watch |
| 4 | `ui/SessionCellRenderer.kt` | Custom JBList cell renderer (card-style with title + subtitle) |
| 5 | `ui/SessionListPanel.kt` | JBList with sessions, click + context menu handlers |
| 6 | `ui/SessionBrowserPanel.kt` | Main panel: toolbar + list |
| 7 | `ui/SessionBrowserToolWindowFactory.kt` | Tool window factory (DumbAware) |
| 8 | `ui/SessionActions.kt` | Context menu actions: Resume, Fork, CopyId, Delete |

## Modified files (1)

| File | Changes |
|------|---------|
| `plugin.xml` | Add `<projectService>` + `<toolWindow anchor="right">` |

## Key implementation details

### Path encoding
Project path to directory name:
```kotlin
fun encodeProjectPath(projectPath: String): String {
    return projectPath.replace("/", "-").removePrefix("-")
}
// /Users/smidl/Desktop/ccup -> Users-smidl-Desktop-ccup
```

### Sessions index location
```kotlin
fun getSessionsIndexPath(projectPath: String): Path {
    val home = System.getProperty("user.home")
    val encoded = encodeProjectPath(projectPath)
    return Path.of(home, ".claude", "projects", encoded, "sessions-index.json")
}
```

### Resume in terminal
```kotlin
fun resumeSession(project: Project, sessionId: String) {
    val terminalView = TerminalView.getInstance(project)
    // Create new terminal tab with command:
    // claude --resume <sessionId>
}
```

Uses IntelliJ Terminal API (plugin already depends on `org.jetbrains.plugins.terminal`).

### VFS auto-refresh
Watch `sessions-index.json` for changes. When Claude Code creates/modifies sessions, the list updates automatically.

## Implementation order

1. **Data model** — SessionEntry
2. **Parser** — SessionIndexParser (Gson-based)
3. **Service** — SessionService (load, resume, fork, delete, VFS watch)
4. **Cell renderer** — SessionCellRenderer (card-style)
5. **List panel** — SessionListPanel with click + context menu
6. **Main panel** — SessionBrowserPanel with toolbar
7. **Factory** — SessionBrowserToolWindowFactory
8. **Registration** — plugin.xml
9. **Build & test**

## Verification

1. Build: `./gradlew buildPlugin`
2. Run: `./gradlew runIde`
3. Verify icon appears on right sidebar stripe
4. Click icon — panel opens with sessions list
5. Verify sessions loaded from `~/.claude/projects/{path}/sessions-index.json`
6. Click session — new terminal tab opens with `claude --resume <id>`
7. Right-click — context menu appears with Resume / Fork / Copy ID / Delete
8. Delete — confirmation dialog, file removed, list refreshed
9. Fork — terminal opens with `claude --resume <id> --fork-session`
