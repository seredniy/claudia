# Skill Manager — Plan

## Idea

Tool Window in the right IDE sidebar for managing Claude Code skills (`.claude/skills/*.md`).

## UI Layout

```
┌─────────────────────────────────────────────────────────────┐
│ Claude Skills                                               │
├─────────────────────────────────────────────────────────────┤
│ [+ New]  [Edit]  [Delete]  [Refresh]  [Open Folder]        │
├─────────────────────────────────────────────────────────────┤
│ ┌──────────────┐ ┌──────────────┐                           │
│ │  Project     │ │  Global      │                           │
│ └──────────────┘ └──────────────┘                           │
├──────────────────────────┬──────────────────────────────────┤
│  Tree (left, 40%)        │  Preview (right, 60%)            │
│                          │                                  │
│  > backend/              │  /backend:deploy                 │
│    - deploy        <---  │                                  │
│    - test                │  Scope: Project                  │
│  - refactor              │  Description: Deploy to prod     │
│  - review                │  Model: claude-sonnet-4-20250514         │
│                          │  Allowed Tools: Bash, Read       │
│                          │  File: /project/.claude/skill... │
│                          │  --------------------------------│
│                          │                                  │
│                          │  Review all changes and deploy   │
│                          │  to the $1 environment.          │
│                          │                                  │
│                          │  Steps:                          │
│                          │  1. Run tests                    │
│                          │  2. Build artifacts              │
│                          │  3. Deploy via !`./deploy.sh`    │
│                          │                                  │
└──────────────────────────┴──────────────────────────────────┘
```

## Create Skill Dialog

```
┌─────────────────────────────────────────┐
│ Create New Skill                        │
├─────────────────────────────────────────┤
│                                         │
│ Name:          [___________________]    │
│ Namespace:     [___________________]    │
│                e.g. 'backend'           │
│ Scope:         [Project        v]       │
│ --------------------------------------- │
│ Description:   [___________________]    │
│ Model:         [___________________]    │
│ Argument Hint: [___________________]    │
│ Allowed Tools: [___________________]    │
│                comma-separated          │
│ --------------------------------------- │
│ Content:                                │
│ ┌─────────────────────────────────────┐ │
│ │                                     │ │
│ │                                     │ │
│ │                                     │ │
│ └─────────────────────────────────────┘ │
│                                         │
│                    [Cancel]  [Create]    │
└─────────────────────────────────────────┘
```

## Two scopes

| Scope | Path | Shared? |
|-------|------|---------|
| **Project** | `<project>/.claude/skills/` | Yes, via git |
| **Global** | `~/.claude/skills/` | No, personal |

Tabs "Project" / "Global" switch the tree. Preview is shared.

## Architecture

```
plugin.xml
  ├── <projectService> SkillManagerService
  └── <toolWindow> SkillManagerToolWindowFactory
          │
          v
    SkillManagerPanel (tabs + splitter + toolbar)
    ├── Tree (JTree + SkillTreeCellRenderer)
    ├── SkillPreviewPanel (metadata + content)
    └── Actions (Create, Edit, Delete, Refresh, OpenFolder)
          │
          v
    SkillManagerService (project-level)
    ├── scanSkills(scope) -> List<SkillDefinition>
    ├── createSkill() / deleteSkill()
    ├── VFS listener (auto-refresh on file changes)
    └── Message Bus -> SKILLS_CHANGED_TOPIC
          │
          v
    SkillFileParser (parse .md frontmatter + body)
          │
          v
    SkillDefinition / SkillMetadata / SkillScope (data model)
```

## New files (10)

| # | File | Purpose |
|---|------|---------|
| 1 | `skills/model/SkillDefinition.kt` | Data classes: SkillDefinition, SkillMetadata, SkillScope |
| 2 | `skills/model/SkillDirectory.kt` | Data class for skills directory |
| 3 | `skills/parser/SkillFileParser.kt` | Parse .md frontmatter + content |
| 4 | `skills/SkillManagerService.kt` | Project service: scan, CRUD, VFS watch |
| 5 | `skills/ui/SkillTreeCellRenderer.kt` | Tree node rendering with icons |
| 6 | `skills/ui/SkillPreviewPanel.kt` | Right-side preview panel |
| 7 | `skills/ui/SkillManagerPanel.kt` | Main UI assembly |
| 8 | `skills/ui/CreateSkillDialog.kt` | Dialog for creating new skill |
| 9 | `skills/ui/SkillActions.kt` | Toolbar actions (5 actions) |
| 10 | `skills/ui/SkillManagerToolWindowFactory.kt` | Tool window factory |

All under `src/main/kotlin/com/example/anthropic/skills/`.

## Modified files (2)

| File | Changes |
|------|---------|
| `plugin.xml` | Add `<projectService>` + `<toolWindow>` |
| `AnthropicBundle.properties` | Add skill-related strings |

## Implementation order

1. **Data model** — SkillDefinition, SkillMetadata, SkillScope, SkillDirectory
2. **Parser** — SkillFileParser (frontmatter extraction, namespace resolution)
3. **Service** — SkillManagerService (scan, CRUD, VFS listener, message bus)
4. **UI leaf components** — SkillTreeCellRenderer, SkillPreviewPanel
5. **Dialog** — CreateSkillDialog
6. **Actions** — toolbar actions
7. **Main panel** — SkillManagerPanel (assembles everything)
8. **Factory** — SkillManagerToolWindowFactory
9. **Registration** — plugin.xml + bundle.properties
10. **Build & test** — run in sandbox IDE

## Future: Skill Repository

Architecture ready for third tab "Repository":
- Add `SkillScope.REPOSITORY` to enum
- New `SkillRepositoryService` fetches catalog from remote API
- "Install" action copies skill to Global or Project scope
- No restructuring needed — only additions

## Verification

1. Build plugin: `./gradlew buildPlugin`
2. Run in sandbox: `./gradlew runIde`
3. Verify Tool Window appears in right sidebar
4. Create `~/.claude/skills/test.md` manually, verify it shows up
5. Create `<project>/.claude/skills/review.md` with frontmatter, verify preview
6. Test Create / Edit / Delete flows
7. Test auto-refresh (edit .md file externally, verify tree updates)
