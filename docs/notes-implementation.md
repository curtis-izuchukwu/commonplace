# Module notebooks implementation report

Implemented 14 October 2026. Notes is a standalone, nonmodal local writing window organised
around the existing account → module → topic hierarchy. Module Overviews and topic sections
are independently editable, searchable, and saved in the existing SQLite database.

## User access and behaviour

- **Main navigation bar → Notebook** opens or focuses the writing window alongside Dashboard, Manage Modules, and Mistake Bank.
- **Modules → Open Notebook** opens the selected module's Overview.
- **Topic Detail → Open Notes** opens that topic's existing note document.
- **Account menu / command palette → Notes** opens the writing window independently.
- Reopening focuses the existing window; reopening after closing restores the last section
  within the current application process.
- The sidebar expands/collapses modules, includes empty sections, scrolls independently,
  and refreshes on focus, explicit refresh, and module/topic management changes.
- Tabs are independent notebook views, starting with a blank tab before section selection.
  Topic and search selections update the current tab. **+** / Ctrl+T duplicates its current
  section, selection, scroll position, and search query; each tab navigates independently.
  Tabs cache visited editors and synchronize shared note content across duplicate views.
  Ctrl+Tab/Ctrl+Shift+Tab switch tabs; Ctrl+W saves and closes the current tab. Failed saves
  keep it open, and closing the last tab leaves a blank notebook view.
- Search includes current unsaved drafts, matches content and section names, identifies
  module/topic, and selects matching content in the editor.
- Theme, accent, font sizing, compact layout, high contrast, larger controls, reduced motion,
  and accessible control labels follow the shared application preferences.

## Main files

Paths below are relative to the repository root.

| Created file | Responsibility |
| --- | --- |
| `src/main/java/com/commonplace/model/NoteKey.java` | Stable module/topic identifiers |
| `src/main/java/com/commonplace/model/NoteDocument.java` | Immutable paragraphs and formatting spans |
| `src/main/java/com/commonplace/model/NoteRecord.java` | Stored content/version/timestamps |
| `src/main/java/com/commonplace/model/NotebookSection.java` | Names derived from existing module/topic records |
| `src/main/java/com/commonplace/model/NoteSearchResult.java` | Search location and contextual snippet |
| `src/main/java/com/commonplace/repository/NoteSchema.java` | Additive, idempotent schema migration |
| `src/main/java/com/commonplace/repository/NoteRepository.java` | Account-scoped retrieval, upserts, lazy empty notes, and structure queries |
| `src/main/java/com/commonplace/service/NoteDocumentCodec.java` | Reversible versioned XML persistence |
| `src/main/java/com/commonplace/service/NoteService.java` | Document lifecycle and search matching |
| `src/main/java/com/commonplace/service/NoteWorkspace.java` | Draft cache, revision tracking, ordered IO, debounce, and recovery |
| `src/main/java/com/commonplace/ui/NoteEditor.java` | RichTextFX adapter, toolbar, keyboard shortcuts, lists, and clipboard |
| `src/main/java/com/commonplace/ui/NotesWindowManager.java` | Stage creation/focus, close guards, account lifecycle, and preferences |
| `src/main/java/com/commonplace/ui/controller/NotesWindowController.java` | Navigation, search, editor status, and recovery UI |
| `src/main/resources/com/commonplace/fxml/NotesWindowView.fxml` | Resizable notebook layout |

Modified files: `pom.xml`, `package-windows.ps1`, `DatabaseManager.java`, `AccountSession.java`,
`DataManagementService.java`, `AppChrome.java`, `ModulesController.java`, `TopicDetailController.java`,
`DashboardController.java`, `SettingsController.java`, `ModulesView.fxml`, `TopicDetailView.fxml`, `DashboardView.fxml`,
`app.css`, `README.md`, `docs/user-guide.md`, and `docs/architecture.md`.

New tests: `NoteRepositoryTest.java`, `NoteDocumentCodecTest.java`, `NoteWorkspaceTest.java`,
and `NotesWindowTest.java`. Captured layouts: `docs/screenshots/notes.png` and `notes-light.png`.

## Editor and storage

RichTextFX **0.11.7** supplies maintained JavaFX editing, selection, rich clipboard operations,
wrapping, scrolling, and undo/redo. It integrates directly with JavaFX 21, shared CSS, and the
existing classpath-based Windows packaging. It avoids introducing a browser/WebView editor or
a custom text editing engine. The toolbar implements bold, italic, underline, monospaced code,
normal paragraphs, Heading 1, Heading 2, bulleted lists, and numbered lists.
Enter in either heading starts a normal paragraph with default text formatting, preserving
the original heading and any remaining text. The change is a single undo/redo operation.

`module_notes.module_id` and `topic_notes.topic_id` are both primary keys and cascading foreign
keys to their existing parents. Each record also has `content`, `plain_text`, `format_version`,
`created_at`, and `updated_at`. Ownership checks join back to the module's account and verify
the requested parent module for a topic. Names are read from the existing records. There are
no duplicate documents or monolithic module documents. Empty notes require no database row;
deliberate whitespace and final line breaks are preserved.

Version-1 UTF-8 XML stores paragraph block types and spans with an inline formatting bit mask.
Ordinary text is XML-escaped; control characters have an explicit lossless encoding. DTDs and
external entities are disabled. Unknown versions or corrupt content fail visibly and remain
unchanged in SQLite. Java object serialization is not used. Search is local substring matching
over derived plain text, with newer cached drafts overlaid on persisted records.

## Autosave and data safety

One background executor handles IO for each account window. Edits update immutable drafts and
reset a **900 ms** timer. Navigation, Ctrl+S, closing either window, sign-out/account switching,
and backup/data operations also flush pending edits. Saves execute sequentially. Completion
acknowledges only the captured revision, so an edit made during a slow write remains dirty.
Controllers reject obsolete async results and mutate controls only on the JavaFX thread.

Failures retain drafts across navigation, show **Save failed**, and provide retry. Closing is
blocked while edits cannot save. **Save recovery copy and close** writes all unsaved documents
and their original IDs to a ZIP before releasing state; the workspace checks that the copy
contains the latest revisions. Account identity is captured for queued work. Direct account
changes cannot discard pending drafts or leave an old editor visible under a new account.

Deleting parents and clearing account data cascade to notes. Export first flushes notes and
uses a consistent SQLite snapshot. Import closes Notes, validates/migrates a temporary
candidate, keeps `appdata.before-import.db`, and preserves the source backup.

## Validation

All validation used isolated test databases, including the packaged-process smoke check.
No production database was used.

| Check | Result |
| --- | --- |
| `mvn test` | 153 tests discovered; 123 passed, 30 opt-in tests skipped; no failures/errors |
| `mvn clean package` (invoked by `package-windows.ps1`) | Passed; application JAR built |
| `mvn -Dcommonplace.uiTest=true test` | 152 tests discovered; 150 passed, 2 optional tests skipped; no failures/errors |
| Final `mvn -Dcommonplace.uiTest=true package` | 153 tests discovered; 151 passed, 2 optional tests skipped; final JAR built |
| Windows `jpackage` application image | Built with RichTextFX/transitive jars and `java.xml` in the private runtime |
| Native packaged-process write/restart/read smoke check | Passed; Unicode text, headings, bold, and code formatting survived a fresh process |
| Real JavaFX Notes tests | 11 passed: formatting/clipboard/history, navigation/search/preferences/resizing, list typing, module/topic entry points/refresh, account isolation, save-failure/retry close, main-window close, and heading Enter behaviour including undo/redo and reopening |
| Heading refinement: `mvn -Dcommonplace.uiTest=true -Dtest=NotesWindowTest package` | Passed; all 11 Notes UI tests executed and the application JAR rebuilt |
| Notebook-view tabs: `mvn -Dcommonplace.uiTest=true -Dtest=NotesWindowTest,NoteWorkspaceTest package` | Passed; 20 Notes UI tests and 8 workspace tests executed, including initial blank tabs, topic replacement, duplication of view position, independent navigation/search, synchronized drafts, concurrent topic loads, renames, safe closure, and retained failed saves |
| Visual inspection | Dark layout and a 700 × 500 light/high-contrast/larger-controls layout inspected |

The two optional checks skipped in the JavaFX-enabled suite are the live Press network test
and the existing whole-app documentation screenshot regeneration test. Notes needs no network
connection. Persistence coverage includes repeated initialization, existing-data migration,
current/older imports, account clearing, ownership rejection, cascade deletion, lazy empty
notes, Unicode/control characters, code, long documents, and unsupported-format retention.
Save lifecycle coverage includes debounce, edits during slow saves, retained drafts, recovery
exports, and protection against closing with an outdated recovery copy or during a write.
Recovery export cancels the debounce timer before releasing draft state. The verification
image's application JAR was refreshed from the final build and its native write/read check repeated successfully.

The verification app image is at `target/notes-package-check/Commonplace/Commonplace.exe`.
The temporary packaging harness and its write/read results are under `target/notes-packaged-smoke/`.
The normal launcher configuration was restored after validation.

## Remaining limitations

- Recovery ZIP restoration is manual; an automatic recovery import workflow is deferred.
- Undo history is retained for topics visited within each tab. A new duplicate starts with
  fresh history; updates from another view of the same section reset the receiving view's history.
- Last-selected section is remembered for the current process; window geometry and selection
  are not persisted across application restarts.
- Search uses straightforward substring matching rather than FTS indexing.
- Embedded images, attachments, collaboration, AI features, and note-based XP/mastery rewards
  remain outside this feature's scope.

All requested acceptance criteria are implemented. Worksheet, attempt, mastery, XP, and
recommendation calculations were not changed; their existing regression tests pass.
