# Commonplace

Commonplace is a local-first JavaFX study app for organising revision material, attempting worksheets, reviewing mistakes, and getting adaptive daily worksheet recommendations.

It is designed around a simple revision loop:

1. Create modules, topics, and worksheets.
2. Attempt questions and self-mark answers.
3. Reflect on weak areas.
4. Save mistakes for later review.
5. Let the app recommend what to practise next.

All study data is stored locally in SQLite.

---

## Features

- Local accounts with sign in, remembered sessions, password changes, and account switching.
- Module and topic management with exam dates, priorities, confidence, and mastery tracking.
- Worksheet creation by manual entry, optional Press generation, or local PDF import.
- Optional image attachments for individual worksheet questions.
- In-app worksheet attempts with answer fields, mark scheme reveal, self-marking, and mistake notes.
- Reflection flow after each attempt.
- Mistake bank with revisit and resolved states.
- Daily adaptive worksheet recommendations based on scores, confidence, difficulty, importance, failure streaks, and mistakes.
- Dashboard with the current recommendation, progress, streak, weak topics, recent attempts, and exam calendar.
- Settings for study preferences, notifications, gamification, data backup, and accessibility.
- Local backup export/import.

---

## Screenshots

| Screen | File |
| --- | --- |
| Dashboard | [docs/screenshots/dashboard.png](docs/screenshots/dashboard.png) |
| Manage Modules | [docs/screenshots/modules.png](docs/screenshots/modules.png) |
| Topic Detail | [docs/screenshots/topic-detail.png](docs/screenshots/topic-detail.png) |
| Worksheet Creation | [docs/screenshots/worksheet-creation.png](docs/screenshots/worksheet-creation.png) |
| Worksheet Detail | [docs/screenshots/worksheet-detail.png](docs/screenshots/worksheet-detail.png) |
| Attempt Flow | [docs/screenshots/attempt-flow.png](docs/screenshots/attempt-flow.png) |
| Reflection | [docs/screenshots/reflection.png](docs/screenshots/reflection.png) |
| Mistake Bank | [docs/screenshots/mistake-bank.png](docs/screenshots/mistake-bank.png) |
| Login | [docs/screenshots/login.png](docs/screenshots/login.png) |
| PDF Import Review | [docs/screenshots/pdf-import-review.png](docs/screenshots/pdf-import-review.png) |
| Settings | [docs/screenshots/settings-overview.png](docs/screenshots/settings-overview.png) |

---

## Worksheet Creation

Commonplace supports three worksheet sources:

| Source | Description |
| --- | --- |
| Manual | Create worksheets question by question. |
| Press | Generate an editable draft from the optional online Press API. |
| PDF import | Extract text and images from a local PDF into an editable draft. |

Press uses this endpoint by default:

```text
https://press-api.izuchukwucur.workers.dev
```

You can override it with:

```text
COMMONPLACE_PRESS_API_BASE_URL
```

or:

```text
-Dcommonplace.press.baseUrl=https://your-endpoint.example
```

Use the base URL (without `/generate`). The system property takes precedence over the environment variable.
Update any custom launch configuration to use these Press setting names.

Press supports **short-answer** and **long-answer** generation, with 1–10 questions and easy, medium,
or hard difficulty. Enter a **Subject** (up to 80 characters) and **Specific topic to generate**
(up to 120 characters) in the Press panel. Neither field is filled from your module or saved topic.
Commonplace sends only these user-entered fields and generation options—not the module name,
existing questions, answers, or attached images. The worksheet still saves under the selected local topic.
Answers and every marking point are retained
in editable mark schemes. Review their accuracy before saving; generated content can be incorrect.

Generation runs in the background with a 90-second request timeout. **Stop generation** keeps your
draft intact. Validation, rate-limit, network, and service errors appear in the editor; you can retry
or continue manually. Saved Press questions use the same local worksheet, attempt, and review flows
as manually created questions, and require no network connection to study.

---

## Question Images

Each question can have one optional image.

- Supported formats: PNG, JPG, JPEG.
- Selected images are copied into the app data folder.
- SQLite stores the copied image path, not the raw image data.
- Images appear only with the question they are attached to.
- Missing image files are handled without crashing the app.

Images are stored under:

```text
~/.commonplace/images/
```

---

## Recommendations

Recommendations use weighted selection rather than always choosing the single highest-scoring worksheet.

The score considers:

- Time since last attempt.
- Latest score.
- Topic confidence.
- Worksheet difficulty.
- Worksheet and topic importance.
- Failure streak.
- Linked unresolved mistakes.
- User study preferences.

The app also tracks daily recommendation history so the same worksheet is not recommended twice in one day.

More detail: [docs/weighted-selection.md](docs/weighted-selection.md)

---

## Local Data

Default data directory:

```text
~/.commonplace/
```

Main files:

| Path | Purpose |
| --- | --- |
| `~/.commonplace/appdata.db` | SQLite database |
| `~/.commonplace/images/` | Copied question images and extracted PDF images |

The database is created automatically when the app starts.

---

## Tech Stack

| Technology | Use |
| --- | --- |
| Java 21 | Application runtime |
| JavaFX 21 | Desktop UI |
| Maven | Build and test tooling |
| SQLite | Local persistence |
| JDBC | Database access |
| Apache PDFBox | PDF text and image extraction |
| JUnit 5 | Tests |

---

## Running Locally

### Requirements

- Java 21
- Maven

Check versions:

```bash
java --version
mvn --version
```

Clone and run:

```bash
git clone https://github.com/curtis-izuchukwu/commonplace.git
cd commonplace
mvn javafx:run
```

Run tests:

```bash
mvn clean test
```

The JavaFX editor tests are opt-in and need a desktop display:

```bash
mvn test -Dcommonplace.uiTest=true
```

To also smoke-test both formats against the deployed Press API (sends synthetic study topics):

```bash
mvn test -Dcommonplace.uiTest=true -Dcommonplace.press.liveTest=true
```

Repository tests use SQLite. To keep test data separate from your normal profile, pass a test-JVM
home directory, for example on Windows:

```powershell
mvn test '-DargLine=-Duser.home=C:/path/to/commonplace/target/test-profile' -Dcommonplace.uiTest=true
```

---

## Architecture

Commonplace uses a layered JavaFX architecture:

| Layer | Responsibility |
| --- | --- |
| FXML and controllers | Screens and user interaction |
| Services | Application workflows and business rules |
| Repositories | SQLite persistence |
| Models | Study, account, worksheet, attempt, and mistake data |

More detail: [docs/architecture.md](docs/architecture.md)

---

## Current Limitations

- Delete account is not implemented yet.
- Cloud sync is not implemented; the app is local-first.
- PDF import quality depends on the source PDF and available local OCR.
- Press generation requires network access.
- Boss worksheets are planned but not implemented yet.

---

## Related Project

| Project | Role |
| --- | --- |
| Commonplace | Local-first JavaFX study tracker |
| Press API | Optional worksheet-generation API |
