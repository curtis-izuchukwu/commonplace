package com.commonplace.service;

import com.commonplace.model.*;
import com.commonplace.repository.NoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class NoteWorkspaceTest {
    @TempDir Path temporary;
    private static final NoteKey A = NoteKey.topic(1, 11), B = NoteKey.overview(1);

    private static class MemoryService extends NoteService {
        final Map<NoteKey, NoteDocument> saved = new ConcurrentHashMap<>();
        final AtomicInteger writes = new AtomicInteger();
        volatile boolean fail;
        volatile CountDownLatch entered, release;
        final CountDownLatch wrote = new CountDownLatch(1);
        MemoryService() { super(new NoteRepository(1)); }
        @Override public NoteDocument load(NoteKey key) { return saved.getOrDefault(key, NoteDocument.empty()); }
        @Override public void save(NoteKey key, NoteDocument document) throws SQLException {
            writes.incrementAndGet();
            if (entered != null) entered.countDown();
            if (release != null) {
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new SQLException("Timed out"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new SQLException(e); }
            }
            if (fail) throw new SQLException("Simulated disk failure");
            saved.put(key, document);
            wrote.countDown();
        }
        @Override public List<NotebookSection> sections() { return List.of(new NotebookSection(B, "Databases", "Overview"), new NotebookSection(A, "Databases", "Joins")); }
        @Override public List<NoteSearchResult> searchableNotes() {
            return sections().stream().filter(s -> saved.containsKey(s.key()))
                    .map(s -> new NoteSearchResult(s, saved.get(s.key()).plainText(), -1)).toList();
        }
    }

    @Test void switchingSectionsRetainsDraftsAndFlushSavesBoth() throws Exception {
        MemoryService service = new MemoryService();
        try (NoteWorkspace workspace = new NoteWorkspace(service, 10000)) {
            workspace.open(A).get(5, TimeUnit.SECONDS);
            workspace.update(A, NoteDocument.plain("Draft topic"));
            workspace.open(B).get(5, TimeUnit.SECONDS);
            workspace.update(B, NoteDocument.plain("Draft overview"));
            assertEquals("Draft topic", workspace.open(A).get(5, TimeUnit.SECONDS).plainText());
            assertEquals(2, workspace.status().unsaved());
            workspace.flush().get(5, TimeUnit.SECONDS);
            assertEquals("Draft topic", service.saved.get(A).plainText());
            assertEquals("Draft overview", service.saved.get(B).plainText());
            assertFalse(workspace.hasPendingChanges());
        }
    }

    @Test void debouncedAutosaveCoalescesEdits() throws Exception {
        MemoryService service = new MemoryService();
        try (NoteWorkspace workspace = new NoteWorkspace(service, 100)) {
            workspace.open(A).get(5, TimeUnit.SECONDS);
            workspace.update(A, NoteDocument.plain("one"));
            workspace.update(A, NoteDocument.plain("two"));
            workspace.update(A, NoteDocument.plain("latest"));
            assertTrue(service.wrote.await(5, TimeUnit.SECONDS));
            workspace.flush().get(5, TimeUnit.SECONDS);
            assertEquals(1, service.writes.get());
            assertEquals("latest", service.saved.get(A).plainText());
        }
    }

    @Test void editingDuringSlowSaveNeverMarksNewerRevisionSavedOrOverwritesIt() throws Exception {
        MemoryService service = new MemoryService();
        service.entered = new CountDownLatch(1);
        service.release = new CountDownLatch(1);
        try (NoteWorkspace workspace = new NoteWorkspace(service, 10000)) {
            workspace.open(A).get(5, TimeUnit.SECONDS);
            workspace.update(A, NoteDocument.plain("old"));
            var firstSave = workspace.flush();
            assertTrue(service.entered.await(5, TimeUnit.SECONDS));
            workspace.update(A, NoteDocument.plain("newest"));
            assertTrue(workspace.hasPendingChanges());
            service.release.countDown();
            firstSave.get(5, TimeUnit.SECONDS);
            assertTrue(workspace.hasPendingChanges());
            workspace.flush().get(5, TimeUnit.SECONDS);
            assertEquals(2, service.writes.get());
            assertEquals("newest", service.saved.get(A).plainText());
            assertFalse(workspace.hasPendingChanges());
        } finally { service.release.countDown(); }
    }

    @Test void failurePreventsClosingAndRemainsRecoverableAfterNavigationAndRetry() throws Exception {
        MemoryService service = new MemoryService();
        service.fail = true;
        NoteWorkspace workspace = new NoteWorkspace(service, 10000);
        try {
            workspace.open(A).get(5, TimeUnit.SECONDS);
            workspace.update(A, NoteDocument.plain("Keep this draft"));
            assertThrows(ExecutionException.class, () -> workspace.flush().get(5, TimeUnit.SECONDS));
            assertEquals(1, workspace.status().failed());
            assertThrows(IllegalStateException.class, workspace::close);
            workspace.open(B).get(5, TimeUnit.SECONDS);
            assertEquals("Keep this draft", workspace.open(A).get(5, TimeUnit.SECONDS).plainText());
            service.fail = false;
            workspace.flush().get(5, TimeUnit.SECONDS);
            assertEquals("Keep this draft", service.saved.get(A).plainText());
            assertEquals(0, workspace.status().failed());
        } finally { service.fail = false; workspace.flush().get(5, TimeUnit.SECONDS); workspace.close(); }
    }

    @Test void searchIncludesUnsavedAndFailedDraftsAndCorrectOffsets() throws Exception {
        MemoryService service = new MemoryService();
        service.saved.put(A, NoteDocument.plain("Old searchable content"));
        try (NoteWorkspace workspace = new NoteWorkspace(service, 10000)) {
            workspace.open(A).get(5, TimeUnit.SECONDS);
            workspace.update(A, NoteDocument.plain("αβ SQL joins 中文"));
            List<NoteSearchResult> result = workspace.search("sql JOINS").get(5, TimeUnit.SECONDS);
            assertEquals(1, result.size());
            assertEquals(A, result.getFirst().section().key());
            assertEquals(3, result.getFirst().matchOffset());
            assertTrue(workspace.search("Old searchable").get(5, TimeUnit.SECONDS).isEmpty());
            assertEquals(1, workspace.search("中文").get(5, TimeUnit.SECONDS).size());
            assertEquals(1, workspace.search("Databases").get(5, TimeUnit.SECONDS).size());
            workspace.flush().get(5, TimeUnit.SECONDS);
        }
    }

    @Test void failedDraftCanBeExportedBeforeClosingWithoutPretendingItWasSaved() throws Exception {
        MemoryService service = new MemoryService();
        service.fail = true;
        NoteWorkspace workspace = new NoteWorkspace(service, 10000);
        workspace.open(A).get(5, TimeUnit.SECONDS);
        NoteDocument draft = NoteDocument.plain("Recover <&> this\n中文\n");
        workspace.update(A, draft);
        assertThrows(IllegalStateException.class, workspace::closeAfterRecovery);
        assertThrows(ExecutionException.class, () -> workspace.flush().get(5, TimeUnit.SECONDS));
        Path recovery = temporary.resolve("recovery.zip");
        workspace.exportRecovery(recovery, 42).get(5, TimeUnit.SECONDS);
        assertTrue(workspace.hasPendingChanges());
        try (ZipFile zip = new ZipFile(recovery.toFile())) {
            assertTrue(new String(zip.getInputStream(zip.getEntry("README.txt")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("account 42"));
            String xml = new String(zip.getInputStream(zip.getEntry("module-1-topic-11.xml")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(draft, new NoteDocumentCodec().decode(xml, 1));
        }
        workspace.closeAfterRecovery();
        assertFalse(service.saved.containsKey(A));
    }

    @Test void editingAfterRecoveryExportRequiresAnUpdatedRecoveryCopy() throws Exception {
        MemoryService service = new MemoryService();
        NoteWorkspace workspace = new NoteWorkspace(service, 10000);
        workspace.open(A).get(5, TimeUnit.SECONDS);
        workspace.update(A, NoteDocument.plain("First draft"));
        workspace.exportRecovery(temporary.resolve("recovery.zip"), 42).get(5, TimeUnit.SECONDS);
        workspace.update(A, NoteDocument.plain("Newer draft"));
        assertThrows(IllegalStateException.class, workspace::closeAfterRecovery);
        workspace.exportRecovery(temporary.resolve("recovery.zip"), 42).get(5, TimeUnit.SECONDS);
        workspace.closeAfterRecovery();
    }

    @Test void recoveryCloseCannotReleaseDraftsDuringAnActiveSave() throws Exception {
        MemoryService service = new MemoryService();
        NoteWorkspace workspace = new NoteWorkspace(service, 10000);
        workspace.open(A).get(5, TimeUnit.SECONDS);
        workspace.update(A, NoteDocument.plain("Recovered and saving"));
        workspace.exportRecovery(temporary.resolve("recovery.zip"), 42).get(5, TimeUnit.SECONDS);
        service.entered = new CountDownLatch(1);
        service.release = new CountDownLatch(1);
        try {
            var save = workspace.flush();
            assertTrue(service.entered.await(5, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, workspace::closeAfterRecovery);
            service.release.countDown();
            save.get(5, TimeUnit.SECONDS);
            workspace.close();
            assertEquals("Recovered and saving", service.saved.get(A).plainText());
        } finally { service.release.countDown(); }
    }
}
