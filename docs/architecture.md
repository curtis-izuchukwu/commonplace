# Commonplace Architecture

Commonplace is a Java 21 desktop application built with JavaFX and backed by a local SQLite database. Its core study workflows are local; Press worksheet generation is the only online application service.

## System overview

```text
JavaFX views and controllers
            |
            v
Application services and learning rules
            |
            v
Repositories and transaction boundary
            |
            v
        SQLite database

External boundaries:
  Press HTTP API
  Local PDF files
  Tesseract or Windows OCR
  Local question-image store
```

The dependency direction is intentionally one way. Models are shared data records; repositories do not depend on JavaFX; services coordinate persistence and business rules; controllers translate user actions into service calls.

## Runtime and bootstrap

`com.commonplace.Main` is the JavaFX entry point. Startup performs the following work:

1. Try to restore the remembered local account.
2. Load either `LoginView.fxml` or `DashboardView.fxml`.
3. Wrap the view in `AppChrome`, which owns the title bar, application icon, window controls, breadcrumb, daily-progress chip, and command palette.
4. Apply the shared stylesheet and size the window to the current display's usable bounds.

`DatabaseManager.connect()` creates the application directory, opens SQLite, configures the connection, initialises missing tables, and applies additive migrations.

## Source layout

| Package | Responsibility |
| --- | --- |
| `com.commonplace.model` | Immutable records and enums for accounts, study structure, attempts, settings, and display data |
| `com.commonplace.repository` | SQLite schema, migrations, account-scoped queries, and persistence operations |
| `com.commonplace.service` | Account, study, attempt, learning, XP, recommendation, reflection, backup, and mistake workflows |
| `com.commonplace.ui` | App chrome, overlays, preferences, icons, animations, image rendering, and shared metadata components |
| `com.commonplace.ui.controller` | FXML controllers and screen-level interaction |
| `com.commonplace.press` | Press configuration, HTTP transport, request/response models, and JSON handling |
| `com.commonplace.importer` | PDF text/image extraction, OCR selection, draft parsing, and import diagnostics |
| `com.commonplace.util` | Date helpers and small reusable utilities |

Resources are under `src/main/resources/com/commonplace/`:

| Directory | Contents |
| --- | --- |
| `fxml/` | Application views |
| `css/` | Shared theme and component styles |
| `assets/` | Application icons, Press symbol, and visual textures |

## Domain model

| Model | Purpose |
| --- | --- |
| `User` | Local account identity |
| `UserSettings` | Per-account appearance, study, reminder, recommendation, gamification, and accessibility settings |
| `UserStats` | XP, session streak, last completion date, and worksheet interval |
| `StudyModule` | Module metadata, priority, and optional exam date |
| `Topic` | Topic metadata, learner confidence, importance, and cached mastery estimate |
| `Worksheet` | Worksheet metadata, source, attempt counts, and score summaries |
| `Question` | Prompt, mark scheme, maximum marks, order, tags, and optional image |
| `WorksheetAttempt` | Completed attempt score, timing, confidence, and reflection fields |
| `Answer` | Submitted answer, self-marking result, and mistake state; additional learning fields remain available through repository evidence queries |
| `MistakeBankItem` | Saved mistake review record |
| `DifficultyLevel`, `ImportanceLevel`, `ConfidenceLevel` | Shared study metadata enums |

Mastery is calculated from answer and recall evidence. The `mastery_score` stored on `topics` is a refreshed cache used by list and dashboard queries, not a manually maintained field.

## SQLite persistence

The default database is `~/.commonplace/appdata.db`. Set the JVM property `commonplace.data.dir` to use a different application directory.

Every new connection receives:

```sql
PRAGMA foreign_keys = ON;
PRAGMA busy_timeout = 5000;
```

### Tables

| Table | Responsibility |
| --- | --- |
| `users` | Local account credentials and timestamps |
| `remembered_session` | The single remembered sign-in |
| `user_settings` | Per-account application preferences |
| `user_stats` | XP, streak, last completion date, and recommendation interval |
| `modules` | Account-owned modules |
| `topics` | Topics belonging to modules |
| `module_notes` | One independently saved Overview document per module |
| `topic_notes` | One independently saved document per topic |
| `worksheets` | Worksheets, source type, optional PDF and mark-scheme paths, study scope, generation subject, and score summaries |
| `questions` | Prompts, mark schemes, order, tags, images, and assessed difficulty |
| `worksheet_attempts` | Completed attempt summaries and optional reflection |
| `answers` | Question-level answer, marks, mistake, assistance, timing, and evidence fields |
| `mistake_bank` | Mistakes saved from attempts |
| `mistake_reviews` | Active-recall answers, outcomes, and assistance state |
| `xp_events` | Idempotent, categorised XP ledger |
| `daily_recommendations` | Current saved recommendation for an account |
| `daily_recommendation_history` | Historical same-day recommendation rows retained for compatibility |
| `recommendation_actions` | Ordered same-day recommendation choices used for deterministic cycling |
| `learning_schema` | Applied learning-schema versions |

Foreign-key cascades remove dependent topics, worksheets, questions, attempts, answers, mistakes, and review evidence when their owning structure is deleted.

### Module notebooks

`NoteSchema` adds `module_notes` and `topic_notes` with `CREATE TABLE IF NOT EXISTS`. Their parent
ID is the primary key and a cascading foreign key. Each row stores `content`, `plain_text`,
`format_version`, `created_at`, and `updated_at`. Empty sections remain logical notes without
rows; removing all text deletes an existing row. Deliberate whitespace and line breaks persist.
Ownership is checked by joining to modules for both reads and writes, including validating the
topic's requested parent module. `NoteRepository` captures its account ID at construction, so
queued background work cannot follow a subsequent sign-in.

`NoteDocument` is an immutable list of paragraphs containing a block type and text spans.
`NoteDocumentCodec` stores version-1 UTF-8 XML, independent of Java serialization or RichTextFX
internals. Block types are NORMAL, HEADING_1, HEADING_2, BULLET, and NUMBERED. Span `marks` are a
bit mask: bold=1, italic=2, underline=4, code=8. XML escaping preserves technical text; spans with
control characters use an explicit `utf16-base64` encoding attribute for lossless restoration. The parser
disables DTDs/external entities and refuses unsupported versions or corrupt documents without
replacing their rows. A derived plain-text column supports local, Unicode-aware substring search.

`NoteService` owns document encoding and repository orchestration. `NoteWorkspace` retains
per-section drafts and revisions, coalesces edits over 900 ms, and serializes all IO through one
scheduled executor. A completed save acknowledges only the captured revision. Newer edits stay
dirty. Navigation queues a flush and reads from the draft cache; search overlays drafts on saved
records. Failed writes retain drafts and prevent closing. An explicit recovery ZIP records all
unsaved documents and original IDs; closing with recovery checks that it contains the latest revisions.

`NoteEditor` adapts RichTextFX 0.11.7 for theme-aware paragraph/span styling and formatting controls.
The library supplies editing, rich clipboard operations, wrapping, scrolling, and undo/redo.
`NotesWindowController` handles the sidebar, async navigation/search, open tabs, status, and recovery UI.
Tabs are independent notebook views, created before selecting sections. Section navigation replaces
the active tab's content; the **+** button duplicates its current section and view position.
Each tab caches its visited editors, preserving selection and undo history during navigation.
Views of the same section synchronize through the account's shared drafts, including cached editors,
so returning to an older view cannot overwrite newer text. Navigation generations belong to each tab.
Tab closure flushes pending notes and retains the tab on failure; closing the last tab creates a blank view.
Generation counters reject outdated asynchronous results. All control mutations and editor
snapshots stay on the FX thread; encoding and SQLite writes stay on the workspace executor.

`NotesWindowManager` creates one independent nonmodal stage, uses `AppChrome`, `AppIcon`, and
`AppPreferences`, remembers the last section for this process, and disposes timers, editor
subscriptions, and the IO worker on close. Close-request filters save before either Notes or
the main application closes. Account transitions flush/close first; a session hook prevents
direct account changes from discarding pending drafts. Settings broadcasts appearance updates,
flushes before exporting, and closes Notes before replacing or clearing the database.

Database exports use `VACUUM INTO` for a consistent snapshot. Imports validate and migrate a
temporary candidate before replacement, keep `appdata.before-import.db`, and leave the source
backup intact. The Windows runtime includes `java.xml` for the document codec; the packaging
script copies RichTextFX and its transitive runtime dependencies through Maven.

New automated coverage lives in `NoteRepositoryTest`, `NoteDocumentCodecTest`, `NoteWorkspaceTest`,
and the opt-in `NotesWindowTest` (`-Dcommonplace.uiTest=true`). UI tests exercise real stages,
formatting, clipboard/history, search, entry points, themes, resizing, save failures, and account/main-window lifecycle.
Recovery import, FTS indexing, and persistent tabs/last-section/window geometry are deferred.

### Account isolation

Repositories scope records to `AccountSession.currentUserId()`. Queries for nested entities join back through topics and modules to verify ownership. Services reject IDs that are not available to the signed-in account.

### Transactions

`DatabaseManager.transaction()` supplies one shared connection to every repository call in the unit of work. Repository `close()` calls release a proxy lease rather than closing the underlying transaction connection. Successful work commits; SQL exceptions, runtime exceptions, and errors roll back.

The complete attempt workflow is transactional: validation, attempt creation, answers, mistake records, worksheet statistics, mastery refresh, XP event creation, and streak updates either commit together or roll back together. Reflection is intentionally separate because an attempt is complete before reflection begins.

### Migration safety

Schema evolution is additive. `DatabaseManager` adds older settings and image columns when missing, migrates the legacy global `user_stats` row to per-account rows, backfills recommendation history, and then runs `LearningSchema`.

Before an older database receives its first learning migration, `VACUUM INTO` creates a consistent one-time copy at:

```text
~/.commonplace/appdata.before-learning-v1.db
```

`LearningSchema` currently adds question difficulty, answer assistance and timing fields, worksheet scope, generation subject, XP events, mistake reviews, recommendation actions, and supporting indexes. Existing attempts are retained and are never replayed for XP.

## Service layer

### Accounts and preferences

| Service | Responsibility |
| --- | --- |
| `AccountService` | Account creation, sign-in, sign-out, remembered sessions, and password changes |
| `AccountSession` | Current in-memory account context |
| `PasswordHasher` | Password hashing and verification |
| `UserSettingsService` | Validation and persistence of per-account settings |
| `DataManagementService` | Database export/import and current-account study-data clearing |

### Study structure and content

| Service | Responsibility |
| --- | --- |
| `ModuleTopicService` | Module and topic creation, lookup, counts, and deletion |
| `StudyStructureService` | Module/topic lists used by import workflows |
| `WorksheetCreationService` | Worksheet/question validation, persistence, scope, lookup, and deletion |
| `QuestionImageStorage` | Copying PNG/JPEG files into the application image directory and resolving stored paths |

### Learning and recommendations

| Service | Responsibility |
| --- | --- |
| `LearningModel` | Pure evidence weighting, mastery, retention, breadth, trend, consistency, weakness, and due-date calculations |
| `LearningService` | Account-scoped worksheet/topic estimates, cached topic refresh, and module aggregation |
| `TopicStatsService` | Learner confidence updates without directly setting mastery |
| `PriorityScoreService` | Normalised recommendation scoring and explanation generation |
| `WorksheetSelectionService` | Eligibility filtering, deterministic ranking, stable current selection, and “Pick another” cycling |
| `DashboardService` | Dashboard aggregate, recommendation lock state, reminders, weak topics, attempts, exams, and study record |

The complete calculation and selection behaviour is documented in [Automatic mastery, XP, and recommendations](learning-model.md).

### Attempts, reflection, and mistakes

| Service | Responsibility |
| --- | --- |
| `AttemptService` | Complete-attempt validation and transactional submission |
| `ReflectionService` | Optional reflection, confidence update, worksheet summaries, and bounded reflection XP |
| `GamificationService` | Practice, reflection, and recall XP; caps; study-habit bands; and streaks |
| `MistakeBankService` | Mistake queries, active-recall review, resolved state, mastery refresh, and recall XP |

## Major workflows

### Worksheet attempt

```text
AttemptWorksheetController
        |
        v
AttemptService.submitAttemptWithReward
        |
        +-- validate the complete worksheet question set
        +-- create worksheet_attempts row
        +-- create answers rows
        +-- create selected mistake_bank rows
        +-- refresh worksheet score statistics
        +-- refresh topic mastery
        +-- create an idempotent practice XP event
        +-- update the session streak when the daily goal is met
```

### Recommendation

```text
DashboardService
    |
    +-- refresh cached topic mastery
    +-- load settings, stats, attempts, mistakes, modules, and reminders
    +-- determine whether the daily recommendation window is locked
    +-- WorksheetSelectionService
            |
            +-- filter eligible saved worksheets
            +-- build automatic learning signals
            +-- PriorityScoreService.evaluate
            +-- sort deterministically
            +-- preserve or advance the current recommendation
```

### Worksheet sources

Manual creation, Press generation, and extracted PDF import converge on the question-based path in
`WorksheetCreationService`. PDF worksheets use a separate source type: they retain their original
document, have no question rows, and accept only an optional overall marked attempt.
All sources share `WorksheetSelectionService` and its ranking, history, and completion rules.
`LearningRepository` feeds overall marks into the common learning model when an attempt has no
individual answers, without double counting attempts that already have question evidence.

| Source | Main boundary |
| --- | --- |
| Manual | `WorksheetCreateController` creates editable question drafts |
| Press | `PressWorksheetGenerationService` calls `PressApiClient` and maps the response to editable drafts |
| PDF | `PdfImportService` extracts and parses a local document before `ImportWorksheetController` presents the draft |
| PDF worksheet | `PdfWorksheetStorage` copies the worksheet and optional mark scheme before `PdfDocumentView` renders either document locally |

## JavaFX UI

FXML declares screen structure while controllers own screen state and event handling.

| View | Controller | Purpose |
| --- | --- | --- |
| `LoginView.fxml` | `LoginController` | Sign-in and account creation |
| `DashboardView.fxml` | `DashboardController` | Home dashboard and navigation |
| `ModulesView.fxml` | `ModulesController` | Module, topic, and recommendation management |
| `TopicDetailView.fxml` | `TopicDetailController` | Mastery evidence and worksheet list |
| `WorksheetCreateView.fxml` | `WorksheetCreateController` | Manual and Press worksheet authoring |
| `ImportWorksheetView.fxml` | `ImportWorksheetController` | Local PDF selection and draft review |
| `PdfWorksheetCreateView.fxml` | `PdfWorksheetCreateController` | Original PDF and optional mark-scheme attachment |
| `WorksheetDetailView.fxml` | `WorksheetDetailController` | Worksheet record, recommendation explanation, and mark schemes |
| `AttemptWorksheetView.fxml` | `AttemptWorksheetController` | Locked-answer self-marking flow |
| `ReflectionView.fxml` | `ReflectionController` | Optional post-attempt reflection |
| `MistakeBankView.fxml` | `MistakeBankController` | Mistake recall and resolution |
| `SettingsView.fxml` | `SettingsController` | Account-specific configuration and data tools |
| `ChangePasswordView.fxml` | `ChangePasswordController` | Password update |

### Shared UI components

| Class | Responsibility |
| --- | --- |
| `AppChrome` | Window frame, title bar, daily chip, command palette, dragging, resizing, and window controls |
| `AppIcon` | Runtime window, taskbar, dialog, and title-bar icons |
| `AppPreferences` | Theme, accent, typography, motion, contrast, and control-size classes |
| `OverlayService` | Modal in-app view lifecycle |
| `UiAnimations` | Motion, transitions, feedback, and reduced-motion handling |
| `LevelUi` | Difficulty indicators, recommendation badges, record stat cells, mastery fills, and ledger rows |
| `LearningUi` | Mastery evidence presentation and mistake-recall interaction |
| `QuestionImageViewFactory` | Question image display and missing-image fallback |

Metadata has three visual roles: recommendation/status badges for immediate scanning, stat cells or ledger rows for stable study records, and muted text for descriptions. Mastery stat cells use a theme-aware translucent fill proportional to the percentage.

## External boundaries

### Press

`PressApiConfig` resolves the base URL from the `commonplace.press.baseUrl` JVM property, then `COMMONPLACE_PRESS_API_BASE_URL`, then the deployed default. `PressApiClient` posts JSON to `/generate` with a 10-second connection timeout and 90-second request timeout.

The request contains only:

```text
subject, topic, difficulty, questionCount, format
```

The module name, saved local topic, existing worksheets, answers, and question images are not sent. The response adapter accepts the generated question, model answer, marking-point array, marks, and type, then retains the marking points in the editable local draft.

The controller owns one cancellable background task. It blocks saving while generation is active, preserves existing draft questions, and surfaces transport or response errors without retrying the POST automatically.

### PDF and OCR

`PdfTextExtractionService` and `PdfImageExtractionService` use PDFBox. `WorksheetDraftParser` converts extracted content into an editable draft with import issues rather than treating parsing as authoritative.

When a PDF contains little selectable text, rendered page images are passed to `LocalOcrService`. It selects the first available engine in this order:

1. Tesseract command-line OCR;
2. Windows Runtime OCR through PowerShell;
3. a no-op result with a visible warning when neither engine is available.

OCR and parsing stay local.

### Local images

Question images are copied into `<data-directory>/images/` and stored in SQLite as relative paths. Legacy absolute paths can still be resolved. PNG, JPG, and JPEG are supported. Missing files produce a UI fallback rather than aborting the worksheet view.

PDF worksheets and mark schemes are validated and copied into `<data-directory>/pdfs/`. SQLite
stores only managed relative paths. `PdfDocumentView` uses PDFBox to render one page at a time on a
daemon task, keeping page navigation responsive without loading an entire paper into memory.

## Testing

Tests are organised alongside the production packages and cover repositories, migrations, learning calculations, XP safeguards, selection behaviour, Press transport and parsing, PDF parsing/OCR boundaries, and JavaFX controllers.

The Maven Surefire configuration sets `commonplace.data.dir` to `target/test-data`, isolating automated tests from the normal profile.

```bash
mvn test
```

JavaFX tests require an available desktop display:

```bash
mvn test "-Dcommonplace.uiTest=true"
```

Live Press requests are excluded unless explicitly enabled:

```bash
mvn test "-Dcommonplace.uiTest=true" "-Dcommonplace.press.liveTest=true"
```

`DocumentationScreenshotTest` renders the checked-in screenshot gallery from the current FXML and seeded data. It runs only when requested and should use a dedicated data directory:

```bash
mvn test "-Dcommonplace.docs.screenshots=true" "-Dtest=DocumentationScreenshotTest" "-Dcommonplace.data.dir=target/docs-data"
```

## Design constraints

- Mastery is derived from evidence; confidence remains learner-reported.
- XP events must remain idempotent and bounded.
- Recommendation generation must rank existing worksheets only.
- Press subject and generation topic must remain explicit user inputs.
- Account-owned data must be checked through the module ownership chain.
- Multi-record study workflows must use `DatabaseManager.transaction()`.
- External content always enters as an editable draft.
- Database backups and file backups (`images/` and `pdfs/`) remain separate until a bundled backup format is introduced.
