package com.commonplace.service;

import com.commonplace.model.*;
import com.commonplace.repository.NoteRepository;

import java.sql.SQLException;
import java.util.List;

public class NoteService {
    private final NoteRepository repository;
    private final NoteDocumentCodec codec = new NoteDocumentCodec();

    public NoteService(long userId) { this(new NoteRepository(userId)); }
    public NoteService(NoteRepository repository) { this.repository = repository; }

    public NoteDocument load(NoteKey key) throws SQLException {
        var record = repository.find(key);
        return record.isEmpty() ? NoteDocument.empty()
                : codec.decode(record.get().content(), record.get().formatVersion());
    }

    public void save(NoteKey key, NoteDocument document) throws SQLException {
        repository.save(key, codec.encode(document), document.plainText(), NoteDocument.FORMAT_VERSION);
    }

    public List<NotebookSection> sections() throws SQLException { return repository.sections(); }
    public List<NoteSearchResult> searchableNotes() throws SQLException { return repository.searchableNotes(); }

    /** Case-insensitive matching with offsets in the original document, including Unicode text. */
    public static int match(String text, String query) {
        for (int i = 0; i <= text.length() - query.length(); i++) {
            if (text.regionMatches(true, i, query, 0, query.length())) return i;
        }
        return -1;
    }
}
