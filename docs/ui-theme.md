# Metropolitan Study Companion

The shared [JavaFX stylesheet](../src/main/resources/com/commonplace/css/app.css) defines a warm study-room theme. Components use JavaFX looked-up colour tokens so forms, overlays, search, and the dashboard share surfaces and states.

| Role | Dark palette |
| --- | --- |
| Background | Ink `#0F1110`, charcoal `#151815`, olive black `#171B16` |
| Surfaces | Olive graphite `#1B211B`, elevated olive `#202720`, control graphite `#252B27` |
| Text | Ivory `#F3EBDD`, paper `#C8BFAF`, muted `#9A9285` |
| Accent identity | Brass by default; Graphite, Forest and Burgundy selectable |
| Warning, error and unresolved mistakes | Fixed burgundy `#5B1F2A` with readable rose text |
| Completion and success | Fixed forest `#1F6B43` with readable sage text |
| Priority and exam markers | Fixed muted brass `#B89258` |

Segoe UI handles controls and reading text; Georgia is limited to the dashboard greeting, using system fonts without a loading dependency. Two faint highlights in a repeating vertical CSS gradient suggest book cloth. Cards stay opaque. The light theme uses warm paper surfaces with dark text.

Primary actions, selected navigation, keyboard focus, active dropdowns, section markers, progress, and selected calendar dates follow the selected accent. Secondary actions use graphite surfaces. Warning, success, and priority tokens remain fixed so changing the app's mood never changes state meaning. Difficulty retains its text and restrained neutral bar count; priority retains a written brass marker. Calendar exams retain labels, an overflow count, a full tooltip, and the monthly agenda. Today has a fixed neutral treatment and explicit text label; clicking or pressing Enter/Space on another day gives it the selected accent state.

The Burgundy theme uses deep wine values for controls and outlines: `#5B1F2A` for primary actions and `#7A2C3A` for visible highlights. Warm paper replaces the previous pink accent text. Both book-cloth gradient highlights use 1.5 percent opacity and remain most visible between opaque cards.

Appearance settings offer Brass, Graphite, Forest, and Burgundy. The settings repository accepts all four names on save and reload. Existing saved Cyan, Blue, Mint, and Rose preferences map to those choices respectively in `AppPreferences`, without a database migration. The Accent control uses a narrow accent edge and soft selected fill; popup selection and focus use the same shared tokens. Mistake review notifications and Mistake Bank card outlines follow the selected accent, while their warning/resolved text and actions retain semantic meaning. Destructive delete icons, completion feedback, and priority chips keep their semantic colours. Light/system theme, compact layout, font size, high contrast, larger controls, and reduced motion remain available.

The recommendation is an elevated surface with a selected-accent marker, clearer topic and daily-progress metadata, and a dominant primary action. The compact Study Record separates Rank, XP, Streak, and Mistakes into journal-like rows; zero mistakes uses success colour and unresolved mistakes use warning colour. The calendar uses a bold, title-case “Exam Calendar” heading with a smaller, muted month/year underneath. Mistake Bank navigation is provided by the dashboard header; the Modules recommendation contains Refresh and Open only.

The custom title bar groups the daily counter and Search as compact utilities. The counter uses a narrow accent edge rather than a solid colour block; Search uses the same four-pixel corner language, and the smaller muted window controls sit behind a soft divider. Hover and keyboard focus remain visible in both themes.

The window starts with a consistent 1000px height, capped to the monitor's usable height, and uses that as its minimum windowed height. Login and page navigation keep those dimensions. Manage Modules has a page scrollbar and preserves the form's preferred height, so controls remain reachable when display space or accessibility settings require scrolling.

FXML style-class lists must be comma-separated (`styleClass="stat-card,study-record"`). Space-separated values become a single class in the FXML loader.

Validation uses Maven tests with an isolated `user.home`, including accent persistence, plus JavaFX scene rendering with sample data at 1480×1000, 1480×760 and 1000×680. Check accent changes through Settings save/reload in both themes, window height across navigation, module form overflow with larger fonts/controls, all FXML views, empty calendar, multiple exams on one date, and keyboard focus when changing shared tokens.

JavaFX gradient and looked-up colour syntax: [JavaFX 21 CSS reference](https://docs.oracle.com/en/java/java-components/javafx/21/docs/javafx.graphics/javafx/scene/doc-files/cssref.html).
