# Commonplace Architecture

Commonplace is a JavaFX desktop app with a local SQLite database. The codebase is organised around a conventional layered architecture:

| Layer | Responsibility |
| --- | --- |
| UI | JavaFX FXML, controllers, overlays, app chrome, preferences, animations, and display helpers |
| Service | Application workflows, business rules, account/session logic, recommendations, attempts, reflection, imports, and generation |
| Repository | JDBC access to SQLite |
| Model | Plain Java records and enums used across the app |
| External adapters | Press API client and local PDF/OCR import helpers |

The intended dependency direction is:

```text
UI -> Services -> Repositories -> SQLite
```

Models are shared across layers as simple data objects.

---

## Packages

| Package | Purpose |
| --- | --- |
| `com.commonplace.model` | Records and enums for users, modules, topics, worksheets, attempts, answers, mistakes, settings, and stats |
| `com.commonplace.repository` | SQLite schema setup, migrations, and repository classes |
| `com.commonplace.service` | Business workflows and app rules |
| `com.commonplace.ui` | Shared JavaFX UI helpers |
| `com.commonplace.ui.controller` | JavaFX controllers for each view |
| `com.commonplace.press` | Press API configuration, client, request/response types, and JSON handling |
| `com.commonplace.importer` | PDF text/image extraction, OCR adapters, import issues, and worksheet draft parsing |
| `com.commonplace.util` | Small utility classes |

---

## Model Layer

Models are plain Java records/enums. They should not contain SQL, JavaFX, or workflow logic.

| Model | Purpose |
| --- | --- |
| `User` | Local account identity |
| `UserSettings` | Per-account settings |
| `UserStats` | Study XP, session streaks, and worksheet interval |
| `StudyModule` | Module, priority, and optional exam date |
| `Topic` | Topic metadata, confidence, importance, and mastery |
| `Worksheet` | Worksheet metadata and attempt statistics |
| `Question` | Prompt, mark scheme, max marks, order, tags, and optional image path |
| `WorksheetAttempt` | Completed worksheet attempt summary |
| `Answer` | Submitted answer, awarded marks, and mistake note |
| `MistakeBankItem` | Stored mistake review item |
| `DifficultyLevel`, `ImportanceLevel`, `ConfidenceLevel` | Shared enums for study metadata |

---

## Repository Layer

Repositories own SQLite access through JDBC. They should not contain JavaFX logic or UI decisions.

| Repository | Purpose |
| --- | --- |
| `DatabaseManager` | Creates `~/.commonplace/appdata.db`, enables foreign keys, sets busy timeout, creates tables, and runs migrations |
| `UserRepository` | Users and password metadata |
| `RememberedSessionRepository` | Stay-signed-in session |
| `UserSettingsRepository` | Per-account settings |
| `UserStatsRepository` | XP, streaks, worksheet interval, and recommendation-window helpers |
| `ModuleRepository` | Modules |
| `TopicRepository` | Topics |
| `WorksheetRepository` | Worksheets and worksheet statistics |
| `QuestionRepository` | Worksheet questions and optional image paths |
| `AttemptRepository` | Worksheet attempts |
| `AnswerRepository` | Attempt answers |
| `MistakeRepository` | Mistake bank items |
| `DailyRecommendationRepository` | Current daily recommendation and same-day recommendation history |
| `LearningRepository` | Automatic topic/worksheet evidence, saved worksheet scopes, mistake risk, and evidence metadata |

### SQLite Data

The main tables are:

| Table | Purpose |
| --- | --- |
| `users` | Local accounts |
| `remembered_session` | Current remembered account |
| `user_settings` | Per-account settings |
| `user_stats` | XP, streaks, and worksheet interval |
| `modules` | Study modules |
| `topics` | Module topics |
| `worksheets` | Worksheets and score history |
| `questions` | Questions, mark schemes, marks, order, tags, and image paths |
| `worksheet_attempts` | Completed attempts and reflection fields |
| `answers` | User answers and marking data |
| `mistake_bank` | Stored mistakes |
| `daily_recommendations` | Current recommendation for a user/day |
| `daily_recommendation_history` | Worksheets already recommended to a user on a date |
| `xp_events` | Idempotent, categorized XP ledger |
| `mistake_reviews` | Active-recall review evidence, including assistance |
| `recommendation_actions` | Same-day worksheet and targeted-generation choices |

SQLite connections are created through `DatabaseManager.connect()`, which applies:

- `PRAGMA foreign_keys = ON`
- `PRAGMA busy_timeout = 5000`
- table creation and safe migrations

The learning migration is additive and creates a one-time
`~/.commonplace/appdata.before-learning-v1.db` safety copy before upgrading an older database.

---

## Service Layer

Services coordinate repositories and enforce application rules.

| Service | Purpose |
| --- | --- |
| `AccountService` | Account creation, sign in, sign out, remembered sessions, and password changes |
| `PasswordHasher` | Password hashing and verification |
| `AccountSession` | In-memory current user context |
| `UserSettingsService` | Loads and saves settings |
| `DataManagementService` | Database export/import and current-account data clearing |
| `ModuleTopicService` | Module/topic creation and lookup workflows |
| `StudyStructureService` | Study structure helpers |
| `WorksheetCreationService` | Worksheet and question persistence |
| `QuestionImageStorage` | Copies selected/generated images into `~/.commonplace/images/` and resolves stored paths |
| `PressWorksheetGenerationService` | Optional generation boundary; resolves API configuration only when requested |
| `LearningModel` | Converts question-level performance, spacing, difficulty, assistance, and recall into evidence and retention estimates |
| `LearningService` | Calculates automatic worksheet, topic, and module mastery, breadth, certainty, retention, and review dates |
| `WorksheetSelectionService` | Deterministically selects among eligible saved worksheets |
| `PriorityScoreService` | Calculates normalized, explainable recommendation priority |
| `AttemptService` | Atomically validates and persists complete attempts, evidence, statistics, mistakes, and XP |
| `ReflectionService` | Stores optional reflection and awards bounded reflection XP |
| `TopicStatsService` | Updates self-reported confidence separately from evidence-based mastery |
| `GamificationService` | Idempotent XP ledger, study-habit bands, and session streaks |
| `MistakeBankService` | Mistake revisit, active recall, assistance, resolve, and reward workflows |
| `DashboardService` | Aggregates dashboard summary data |

### Recommendation Flow

1. `DashboardService` asks `WorksheetSelectionService` for the current recommendation.
2. `WorksheetSelectionService` loads automatic topic/worksheet mastery, uncertainty, retention, saved scope, preferences, history, and mistakes.
3. `PriorityScoreService` scores each eligible saved worksheet on normalized factors and produces a matching explanation.
4. The highest-priority unused worksheet is selected deterministically; **Pick another** advances through the ranked alternatives.
5. If no eligible saved worksheet exists, no recommendation is shown. Press generation remains a user-initiated worksheet creation action.
6. `recommendation_actions` records same-day worksheet choices. Meeting the daily goal pauses automatic recommendations until the configured worksheet interval refreshes, without blocking manually opened practice.

### Attempt and Reflection Flow

1. User opens a worksheet from the dashboard, module manager, or topic detail.
2. The user writes and locks each answer before revealing its mark scheme; hints are recorded as assistance.
3. `AttemptService` validates the complete question set and saves the attempt, answers, mistakes, statistics, learning evidence, and practice XP in one transaction.
4. `LearningService` refreshes automatic mastery, evidence breadth, certainty, retention, topic mastery, and module mastery.
5. The saved attempt is immediately complete. `ReflectionService` can subsequently add optional reflection and small bounded XP.
6. Mistake-bank active recall supplies later evidence and earns XP only for successful unassisted retrieval.

### Import and Generation Flow

Manual creation, Press generation, and PDF import all end by saving normal worksheets/questions through the worksheet creation path.

| Source | Main classes |
| --- | --- |
| Manual | `WorksheetCreateController`, `WorksheetCreationService` |
| Press | `PressApiClient`, `PressWorksheetGenerationService`, `WorksheetCreateController` |
| PDF import | `PdfImportService`, `PdfTextExtractionService`, `PdfImageExtractionService`, OCR services, `WorksheetDraftParser`, `ImportWorksheetController` |

Question images are copied into local app data before the question is saved.

---

## UI Layer

FXML files define views; controllers handle user actions and call services. Controllers should not write SQL directly.

| View | Controller | Purpose |
| --- | --- | --- |
| `LoginView.fxml` | `LoginController` | Sign in and account creation |
| `DashboardView.fxml` | `DashboardController` | Dashboard, navigation, recommendation, progress, recent activity, and exam calendar |
| `ModulesView.fxml` | `ModulesController` | Module/topic management and module recommendation panel |
| `TopicDetailView.fxml` | `TopicDetailController` | Topic details and worksheet list |
| `WorksheetCreateView.fxml` | `WorksheetCreateController` | Manual and Press worksheet creation |
| `ImportWorksheetView.fxml` | `ImportWorksheetController` | PDF import and review |
| `WorksheetDetailView.fxml` | `WorksheetDetailController` | Worksheet details and recommendation explanation |
| `AttemptWorksheetView.fxml` | `AttemptWorksheetController` | Worksheet attempts |
| `ReflectionView.fxml` | `ReflectionController` | Post-attempt reflection |
| `MistakeBankView.fxml` | `MistakeBankController` | Mistake review |
| `SettingsView.fxml` | `SettingsController` | Account, study, notification, gamification, data, and accessibility settings |
| `ChangePasswordView.fxml` | `ChangePasswordController` | Password changes |

Shared UI helpers:

| Class | Purpose |
| --- | --- |
| `AppChrome` | Main window wrapper, title bar, command palette, and window controls |
| `AppIcon` | Shared app icon loading/application |
| `AppPreferences` | Applies saved settings to JavaFX roots/scenes |
| `OverlayService` | Opens and closes in-app overlay views |
| `UiAnimations` | Shared animation/feedback helpers |
| `LevelUi` | Difficulty/priority visual helpers |
| `QuestionImageViewFactory` | JavaFX image previews and missing-image fallback |

---

## External Adapters

### Press

The `press` package isolates online worksheet generation.

| Class | Purpose |
| --- | --- |
| `PressApiConfig` | Base URL from system property, environment variable, or default endpoint |
| `PressApiClient` | HTTP client for `/generate` |
| `PressGenerateRequest` | Generation request |
| `PressGenerateResponse` | Generation response |
| `PressGeneratedQuestion` | Generated question DTO |
| `PressQuestionFormat` | Supported question format enum |
| `PressJson` | Lightweight JSON handling |

`POST /generate` sends `subject`, `topic`, `difficulty`, `questionCount` (1–10), and `format`
(`short-answer` or `long-answer`). The deployed response contains `metadata` and a `questions` array;
each question provides `question`, `answer`, `markScheme` (an array of marking points), `marks`, and `type`.
The adapter retains marking points as separate lines and the model answer in `QuestionDraft.markScheme`,
and tags new drafts with `press` and their format. Existing saved questions need no migration.

`WorksheetCreateController` takes the API subject and topic exclusively from the user-entered
Press fields (80 and 120 characters maximum). The module and saved topic are only used for local
organisation; they are not automatically added to generation requests. The captured request topic
supplies a suggested worksheet title when Press returns no title. Saving still uses the original local topic ID.

The JavaFX controller owns one cancellable background task. It validates editable spinners before
sending or saving, preserves existing drafts, and prevents saving while generation is pending.
Closing the editor or stopping generation interrupts the request. The HTTP adapter has a 10-second
connection timeout and 90-second request timeout, surfaces API errors, and does not automatically
retry POST requests. No API history is fetched; Commonplace's local worksheets remain the source of truth.

### PDF/OCR Import

The `importer` package isolates local PDF import.

| Class/group | Purpose |
| --- | --- |
| `PdfTextExtractionService` | Selectable text extraction |
| `PdfImageExtractionService` | Embedded image extraction |
| `OcrService` implementations | Optional OCR fallback |
| `WorksheetDraftParser` | Converts extracted text/images into editable question drafts |
| `ImportedWorksheetDraft`, `ImportedQuestionDraft` | Draft models shown in the import review UI |
| `ImportIssue` | Import warnings/info for the user |

---

## Rules and Boundaries

| Rule | Reason |
| --- | --- |
| Models stay simple | They are shared data objects |
| Controllers stay UI-focused | UI code should not own persistence or scoring rules |
| Services own workflows | Business behaviour belongs in one testable layer |
| Repositories own persistence | SQL and schema details stay in one layer |
| Import/API adapters stay isolated | External parsing and network details should not leak into controllers |
| Local files are stored under app data | User-selected question images should not rely on original absolute paths |

---

## Data Location

Default app data directory:

```text
~/.commonplace/
```

Important paths:

| Path | Purpose |
| --- | --- |
| `~/.commonplace/appdata.db` | SQLite database |
| `~/.commonplace/images/` | Copied question images and extracted PDF images |
