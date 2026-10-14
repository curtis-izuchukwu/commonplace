package com.commonplace.service;

import com.commonplace.model.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Owns immutable drafts and one ordered IO queue for a single account window.
 * No JavaFX dependency: callers dispatch completions to the application thread.
 */
public final class NoteWorkspace implements AutoCloseable {
    public record Status(int unsaved, int failed, boolean saving, Instant lastSaved, String error) {}

    private static final class Draft {
        NoteDocument document;
        long revision, savedRevision;
        String error;
        Draft(NoteDocument document) { this.document = document; }
        boolean dirty() { return revision != savedRevision; }
    }

    private final NoteService service;
    private final ScheduledExecutorService worker;
    private final long debounceMillis;
    private final Map<NoteKey, Draft> drafts = new LinkedHashMap<>();
    private ScheduledFuture<?> pendingSave;
    private Consumer<Status> statusListener = ignored -> {};
    private boolean saving, closed;
    private Instant lastSaved;
    private Map<NoteKey, Long> recoveredRevisions = Map.of();

    public NoteWorkspace(NoteService service) { this(service, 900); }
    public NoteWorkspace(NoteService service, long debounceMillis) {
        this.service = service;
        this.debounceMillis = debounceMillis;
        worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "commonplace-notes-io");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void setStatusListener(Consumer<Status> listener) {
        statusListener = Objects.requireNonNull(listener);
    }

    public CompletableFuture<NoteDocument> open(NoteKey key) {
        return submit(() -> {
            synchronized (this) {
                if (drafts.containsKey(key)) return drafts.get(key).document;
            }
            NoteDocument loaded = service.load(key);
            synchronized (this) {
                requireOpen();
                drafts.putIfAbsent(key, new Draft(loaded));
                return drafts.get(key).document;
            }
        });
    }

    /** Called with an FX-thread snapshot; neither SQLite nor encoding runs here. */
    public synchronized void update(NoteKey key, NoteDocument document) {
        requireOpen();
        Draft draft = drafts.get(key);
        if (draft == null) throw new IllegalStateException("Load the note before editing it.");
        if (draft.document.equals(document)) return;
        draft.document = document;
        draft.revision++;
        if (pendingSave != null) pendingSave.cancel(false);
        pendingSave = worker.schedule(() -> saveAll(), debounceMillis, TimeUnit.MILLISECONDS);
        publish();
    }

    public CompletableFuture<List<NotebookSection>> sections() { return submit(service::sections); }

    /** Searches persisted notes and overlays newer drafts, including failed saves. */
    public CompletableFuture<List<NoteSearchResult>> search(String query) {
        return submit(() -> {
            String term = query.trim();
            if (term.isEmpty()) return List.of();
            Map<NoteKey, NoteSearchResult> candidates = new LinkedHashMap<>();
            service.searchableNotes().forEach(note -> candidates.put(note.section().key(), note));
            Map<NoteKey, NotebookSection> sections = new HashMap<>();
            service.sections().forEach(section -> sections.put(section.key(), section));
            synchronized (this) {
                drafts.forEach((key, draft) -> {
                    NotebookSection section = sections.get(key);
                    if (section != null) candidates.put(key, new NoteSearchResult(section, draft.document.plainText(), -1));
                    else candidates.remove(key);
                });
            }
            List<NoteSearchResult> results = new ArrayList<>();
            for (NoteSearchResult candidate : candidates.values()) {
                int match = NoteService.match(candidate.plainText(), term);
                if (match >= 0 || NoteService.match(candidate.section().moduleName(), term) >= 0
                        || NoteService.match(candidate.section().title(), term) >= 0) {
                    results.add(new NoteSearchResult(candidate.section(), candidate.plainText(), match));
                }
            }
            results.sort(Comparator.comparing(result -> result.section().moduleName() + " " + result.section().title()));
            return List.copyOf(results);
        });
    }

    /** Enqueued on navigation, Ctrl+S, closing, and account/data transitions. */
    public synchronized CompletableFuture<Void> flush() {
        requireOpen();
        if (pendingSave != null) pendingSave.cancel(false);
        return submit(() -> {
            Throwable failure = saveAll();
            if (failure != null) throw new CompletionException(failure);
            return null;
        });
    }

    private Throwable saveAll() {
        List<NoteKey> keys;
        synchronized (this) {
            if (closed) return null;
            keys = new ArrayList<>(drafts.keySet());
        }
        Throwable failure = null;
        for (NoteKey key : keys) {
            NoteDocument snapshot;
            long revision;
            synchronized (this) {
                if (closed) return failure;
                Draft draft = drafts.get(key);
                if (draft == null || !draft.dirty()) continue;
                snapshot = draft.document;
                revision = draft.revision;
                saving = true;
                publish();
            }
            try {
                service.save(key, snapshot);
                synchronized (this) {
                    Draft draft = drafts.get(key);
                    // An edit made during this save remains dirty. Saves never run concurrently.
                    draft.savedRevision = revision;
                    draft.error = null;
                    lastSaved = Instant.now();
                }
            } catch (Exception e) {
                failure = e;
                synchronized (this) { drafts.get(key).error = e.getMessage(); }
            } finally {
                synchronized (this) { saving = false; publish(); }
            }
        }
        return failure;
    }

    public synchronized boolean hasPendingChanges() {
        return saving || drafts.values().stream().anyMatch(Draft::dirty);
    }

    public synchronized Status status() {
        int unsaved = 0, failed = 0;
        String error = null;
        for (Draft draft : drafts.values()) {
            if (draft.dirty()) unsaved++;
            if (draft.error != null) { failed++; error = draft.error; }
        }
        return new Status(unsaved, failed, saving, lastSaved, error);
    }

    private void publish() { statusListener.accept(status()); }

    /** Explicit recovery path when SQLite cannot save, including deleted parent records. */
    public CompletableFuture<Void> exportRecovery(Path target, long accountId) {
        Map<NoteKey, NoteDocument> snapshots = new LinkedHashMap<>();
        Map<NoteKey, Long> revisions = new HashMap<>();
        synchronized (this) {
            requireOpen();
            if (pendingSave != null) pendingSave.cancel(false);
            drafts.forEach((key, draft) -> {
                if (draft.dirty()) { snapshots.put(key, draft.document); revisions.put(key, draft.revision); }
            });
        }
        return submit(() -> {
            Path absolute = target.toAbsolutePath();
            Path temporary = Files.createTempFile(absolute.getParent(), "notes-recovery-", ".zip");
            try {
                try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
                    zip.putNextEntry(new ZipEntry("README.txt"));
                    zip.write(("Commonplace recovery copy for local account " + accountId + ".\n"
                            + "These version-1 XML documents preserve paragraphs and formatting.\n"
                            + "Filenames identify the original module and topic IDs.\n"
                            + "Keep this copy; recovery import is currently manual.\n").getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                    NoteDocumentCodec codec = new NoteDocumentCodec();
                    for (var entry : snapshots.entrySet()) {
                        NoteKey key = entry.getKey();
                        zip.putNextEntry(new ZipEntry("module-" + key.moduleId() + "-"
                                + (key.isOverview() ? "overview" : "topic-" + key.topicId()) + ".xml"));
                        zip.write(codec.encode(entry.getValue()).getBytes(StandardCharsets.UTF_8));
                        zip.closeEntry();
                    }
                }
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
                synchronized (this) { recoveredRevisions = Map.copyOf(revisions); }
            } finally { Files.deleteIfExists(temporary); }
            return null;
        });
    }

    @FunctionalInterface private interface Work<T> { T run() throws Exception; }
    private synchronized <T> CompletableFuture<T> submit(Work<T> work) {
        requireOpen();
        CompletableFuture<T> future = new CompletableFuture<>();
        worker.execute(() -> {
            try { future.complete(work.run()); }
            catch (Exception e) { future.completeExceptionally(e); }
        });
        return future;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("The notes workspace is closed.");
    }

    @Override public synchronized void close() {
        if (hasPendingChanges()) throw new IllegalStateException("Save notes or export a recovery copy before closing.");
        dispose();
    }

    /** Only called after an explicit, successful recovery export with editing locked. */
    public synchronized void closeAfterRecovery() {
        if (saving) throw new IllegalStateException("Wait for the current save before closing with recovery.");
        for (var entry : drafts.entrySet()) {
            if (entry.getValue().dirty() && !Objects.equals(recoveredRevisions.get(entry.getKey()), entry.getValue().revision)) {
                throw new IllegalStateException("Save a recovery copy of the latest changes before closing.");
            }
        }
        dispose();
    }

    private void dispose() {
        closed = true;
        statusListener = ignored -> {};
        if (pendingSave != null) pendingSave.cancel(false);
        worker.shutdownNow();
        drafts.clear();
    }
}
