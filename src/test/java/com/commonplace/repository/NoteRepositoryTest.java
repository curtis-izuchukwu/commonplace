package com.commonplace.repository;

import com.commonplace.model.*;
import com.commonplace.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NoteRepositoryTest {
    @TempDir Path temporary;
    private User previous, owner, other;
    private StudyModule module;
    private Topic topic;
    private NoteRepository repository;
    private NoteService service;

    @BeforeEach void setUp() throws Exception {
        previous = AccountSession.currentUser().orElse(null);
        UserRepository users = new UserRepository();
        owner = users.create("note-owner-" + UUID.randomUUID(), "hash", "salt");
        other = users.create("note-other-" + UUID.randomUUID(), "hash", "salt");
        AccountSession.signIn(owner);
        module = new ModuleRepository().create("Data Engineering", "Keep this description", null, ImportanceLevel.MEDIUM);
        topic = new TopicRepository().create(module.id(), "SQL joins", "Short topic description", ImportanceLevel.HIGH, ConfidenceLevel.MEDIUM);
        repository = new NoteRepository();
        service = new NoteService(repository);
    }

    @AfterEach void cleanUp() throws Exception {
        try (Connection conn = DatabaseManager.connect(); PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE id IN (?, ?)")) {
            stmt.setLong(1, owner.id()); stmt.setLong(2, other.id()); stmt.executeUpdate();
        }
        if (previous == null) AccountSession.signOut(); else AccountSession.signIn(previous);
    }

    private NoteKey key() { return NoteKey.topic(module.id(), topic.id()); }

    @Test void createUpdateReopenFormattingAndTimestampsWithoutDuplicates() throws Exception {
        NoteDocument original = richDocument();
        service.save(key(), original);
        NoteRecord first = repository.find(key()).orElseThrow();
        assertEquals(original, new NoteService(owner.id()).load(key()));
        NoteDocument changed = NoteDocument.plain("Updated\n\nSecond paragraph\n");
        service.save(key(), changed);
        NoteRecord second = repository.find(key()).orElseThrow();
        assertEquals(first.createdAt(), second.createdAt());
        assertFalse(second.updatedAt().isBefore(first.updatedAt()));
        assertEquals(changed, service.load(key()));
        assertEquals(1, count("topic_notes", "topic_id", topic.id()));
        assertEquals("Short topic description", new TopicRepository().findById(topic.id()).orElseThrow().description());
    }

    @Test void overviewAndTopicAreIndependentAndEmptyNotesStayLazy() throws Exception {
        assertEquals(NoteDocument.empty(), service.load(key()));
        service.save(key(), NoteDocument.empty());
        assertEquals(0, count("topic_notes", "topic_id", topic.id()));
        service.save(NoteKey.overview(module.id()), NoteDocument.plain("Module-wide context"));
        service.save(key(), richDocument());
        assertEquals("Module-wide context", service.load(NoteKey.overview(module.id())).plainText());
        assertEquals(richDocument(), service.load(key()));
        service.save(key(), NoteDocument.empty());
        assertEquals(0, count("topic_notes", "topic_id", topic.id()));
        // Deliberate whitespace and final blank paragraphs are retained.
        service.save(key(), NoteDocument.plain(" \n\n"));
        assertEquals(" \n\n", service.load(key()).plainText());
    }

    @Test void unicodeCodeAndLongDocumentsRoundTrip() throws Exception {
        String content = "∀x ∈ ℝ — café, Ελληνικά, 中文, 👩🏽‍💻\nSELECT * FROM users WHERE name = '<&>';\n\treturn a <= b;\n".repeat(2000);
        NoteDocument document = NoteDocument.plain(content);
        service.save(key(), document);
        assertEquals(content, service.load(key()).plainText());
        assertEquals(document, service.load(key()));
    }

    @Test void anotherAccountCannotRetrieveModifyDeleteOrSearchTheseNotes() throws Exception {
        service.save(key(), richDocument());
        AccountSession.signIn(other);
        NoteRepository foreign = new NoteRepository();
        assertThrows(SQLException.class, () -> foreign.find(key()));
        assertThrows(SQLException.class, () -> foreign.save(key(), "bad", "bad", 1));
        assertThrows(SQLException.class, () -> foreign.save(key(), "", "", 1));
        assertTrue(foreign.sections().isEmpty());
        assertTrue(foreign.searchableNotes().isEmpty());
        assertEquals(richDocument(), service.load(key())); // Already queued operations keep their captured account.
        assertEquals(1, count("topic_notes", "topic_id", topic.id()));
    }

    @Test void wrongParentModuleCannotBeUsedEvenWithinSameAccount() throws Exception {
        StudyModule second = new ModuleRepository().create("Other notebook", "", null, ImportanceLevel.LOW);
        NoteKey mismatched = NoteKey.topic(second.id(), topic.id());
        assertThrows(SQLException.class, () -> service.load(mismatched));
        assertThrows(SQLException.class, () -> service.save(mismatched, richDocument()));
    }

    @Test void renamingParentsKeepsNotesAndNavigationUsesCurrentNames() throws Exception {
        service.save(key(), richDocument());
        new ModuleRepository().update(new StudyModule(module.id(), "Renamed module", module.description(), null,
                module.importance(), module.createdAt(), module.updatedAt()));
        new TopicRepository().update(new Topic(topic.id(), module.id(), "Renamed topic", topic.description(),
                topic.importance(), topic.confidence(), topic.masteryScore(), topic.createdAt(), topic.updatedAt()));
        assertEquals(richDocument(), service.load(key()));
        NotebookSection section = service.sections().stream().filter(s -> s.key().equals(key())).findFirst().orElseThrow();
        assertEquals("Renamed module", section.moduleName());
        assertEquals("Renamed topic", section.title());
    }

    @Test void parentDeletionCascadesWithoutOrphans() throws Exception {
        service.save(key(), richDocument());
        service.save(NoteKey.overview(module.id()), richDocument());
        new TopicRepository().deleteById(topic.id());
        assertEquals(0, count("topic_notes", "topic_id", topic.id()));
        assertThrows(SQLException.class, () -> service.save(key(), richDocument()));
        Topic second = new TopicRepository().create(module.id(), "Transactions", "", ImportanceLevel.LOW, ConfidenceLevel.HIGH);
        service.save(NoteKey.topic(module.id(), second.id()), richDocument());
        new ModuleRepository().deleteById(module.id());
        assertEquals(0, count("topic_notes", "topic_id", second.id()));
        assertEquals(0, count("module_notes", "module_id", module.id()));
        try (Connection conn = DatabaseManager.connect(); var stmt = conn.createStatement(); var rs = stmt.executeQuery("PRAGMA foreign_key_check")) {
            assertFalse(rs.next());
        }
    }

    @Test void clearingOneAccountCascadesItsNotesAndPreservesTheOther() throws Exception {
        service.save(key(), richDocument());
        service.save(NoteKey.overview(module.id()), richDocument());
        AccountSession.signIn(other);
        StudyModule otherModule = new ModuleRepository().create("Other account", "", null, ImportanceLevel.LOW);
        NoteService otherService = new NoteService(other.id());
        otherService.save(NoteKey.overview(otherModule.id()), NoteDocument.plain("Keep me"));
        AccountSession.signIn(owner);
        new DataManagementService().clearCurrentAccountData();
        assertEquals(0, count("topic_notes", "topic_id", topic.id()));
        assertEquals(0, count("module_notes", "module_id", module.id()));
        assertEquals("Keep me", otherService.load(NoteKey.overview(otherModule.id())).plainText());
    }

    @Test void repeatedInitializationPreservesExistingStudyDataAndNotes() throws Exception {
        service.save(key(), richDocument());
        try (Connection conn = DatabaseManager.connect()) {
            DatabaseManager.initialiseTables(conn);
            DatabaseManager.initialiseTables(conn);
        }
        assertEquals(richDocument(), service.load(key()));
        assertEquals(module, new ModuleRepository().findById(module.id()).orElseThrow());
        assertEquals(topic, new TopicRepository().findById(topic.id()).orElseThrow());
    }

    @Test void preNotesDatabaseMigratesAdditivelyWithStudyRecordsIntact() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:"); Statement stmt = conn.createStatement()) {
            DatabaseManager.initialiseTables(conn);
            stmt.execute("DROP TABLE module_notes"); stmt.execute("DROP TABLE topic_notes");
            stmt.execute("INSERT INTO modules(id,name,created_at,updated_at) VALUES(1,'Existing module','2024-01-01T12:00','2024-01-01T12:00')");
            stmt.execute("INSERT INTO topics(id,module_id,name,created_at,updated_at) VALUES(2,1,'Existing topic','2024-01-01T12:00','2024-01-01T12:00')");
            stmt.execute("INSERT INTO worksheets(id,topic_id,title,created_at) VALUES(3,2,'Existing worksheet','2024-01-01T12:00')");
            DatabaseManager.initialiseTables(conn);
            DatabaseManager.initialiseTables(conn);
            try (var rs = stmt.executeQuery("SELECT title FROM worksheets WHERE id=3")) {
                assertTrue(rs.next()); assertEquals("Existing worksheet", rs.getString(1));
            }
            try (var rs = stmt.executeQuery("SELECT COUNT(*) FROM topic_notes")) { assertTrue(rs.next()); assertEquals(0, rs.getInt(1)); }
        }
    }

    @Test void backupContainsNotesAndMalformedImportLeavesDatabaseUntouched() throws Exception {
        service.save(key(), richDocument());
        Path backup = temporary.resolve("notes.db");
        DataManagementService management = new DataManagementService();
        management.exportDatabase(backup);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + backup);
             PreparedStatement stmt = conn.prepareStatement("SELECT content FROM topic_notes WHERE topic_id=?")) {
            stmt.setLong(1, topic.id());
            try (var rs = stmt.executeQuery()) {
                assertTrue(rs.next()); assertEquals(repository.find(key()).orElseThrow().content(), rs.getString(1));
            }
        }
        Path invalid = temporary.resolve("invalid.db");
        java.nio.file.Files.writeString(invalid, "Not a SQLite database");
        assertThrows(java.io.IOException.class, () -> management.importDatabase(invalid));
        assertEquals(richDocument(), service.load(key()));
    }

    private static long count(String table, String column, long id) throws Exception {
        try (Connection conn = DatabaseManager.connect(); PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?")) {
            stmt.setLong(1, id);
            try (var rs = stmt.executeQuery()) { assertTrue(rs.next()); return rs.getLong(1); }
        }
    }

    @Test void importingCurrentAndOlderBackupsRestoresOrMigratesNotesSafely() throws Exception {
        DataManagementService management = new DataManagementService();
        service.save(key(), richDocument());
        Path backup = temporary.resolve("current.db");
        management.exportDatabase(backup);
        service.save(key(), NoteDocument.plain("Changed after backup"));
        management.importDatabase(backup);
        assertEquals(richDocument(), new NoteService(owner.id()).load(key()));
        Path older = temporary.resolve("pre-notes.db");
        java.nio.file.Files.copy(backup, older);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + older); var stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE module_notes"); stmt.execute("DROP TABLE topic_notes");
        }
        management.importDatabase(older);
        assertEquals(NoteDocument.empty(), service.load(key()));
        assertEquals("Short topic description", new TopicRepository().findById(topic.id()).orElseThrow().description());
        // Migration occurs on a candidate copy, preserving the original backup file.
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + older); var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE name='topic_notes'")) {
            assertTrue(rs.next()); assertEquals(0, rs.getInt(1));
        }
    }

    @Test void unsupportedDocumentVersionIsRetainedInsteadOfOverwritten() throws Exception {
        repository.save(key(), "Future document content", "Future plain text", 2);
        assertThrows(IllegalArgumentException.class, () -> service.load(key()));
        assertEquals("Future document content", repository.find(key()).orElseThrow().content());
    }

    private static NoteDocument richDocument() {
        return new NoteDocument(List.of(
                new NoteDocument.Paragraph(NoteDocument.Block.HEADING_1, List.of(new NoteDocument.Span("Primary keys", NoteDocument.BOLD))),
                new NoteDocument.Paragraph(NoteDocument.Block.NORMAL, List.of(new NoteDocument.Span("Café & λ < 2", NoteDocument.ITALIC | NoteDocument.UNDERLINE))),
                new NoteDocument.Paragraph(NoteDocument.Block.BULLET, List.of(new NoteDocument.Span("SELECT * FROM t;", NoteDocument.CODE))),
                new NoteDocument.Paragraph(NoteDocument.Block.NUMBERED, List.of(new NoteDocument.Span("First", 0))),
                new NoteDocument.Paragraph(NoteDocument.Block.HEADING_2, List.of(new NoteDocument.Span("Subheading", 0))),
                new NoteDocument.Paragraph(NoteDocument.Block.NORMAL, List.of(new NoteDocument.Span("", 0)))));
    }
}
