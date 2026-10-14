package com.commonplace.service;

import com.commonplace.model.NoteDocument;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NoteDocumentCodecTest {
    private final NoteDocumentCodec codec = new NoteDocumentCodec();

    @Test void allBlocksAndFormattingCombinationsAreReversible() {
        for (var block : NoteDocument.Block.values()) {
            for (int marks = 0; marks <= NoteDocument.ALL_MARKS; marks++) {
                NoteDocument document = new NoteDocument(List.of(new NoteDocument.Paragraph(block, List.of(
                        new NoteDocument.Span("é 中文 👋 <&> \" ' \t", marks), new NoteDocument.Span("tail", 0))),
                        new NoteDocument.Paragraph(NoteDocument.Block.NORMAL, List.of())));
                assertEquals(document, codec.decode(codec.encode(document), 1));
            }
        }
        assertEquals(NoteDocument.empty(), codec.decode(codec.encode(NoteDocument.empty()), 1));
    }

    @Test void corruptOrFutureDocumentsCannotBecomeEmptyEditableNotes() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("<note version='2'/>", 2));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("<note version='1'><p>", 1));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("<note version='1'><p block='NORMAL'><span marks='16'>x</span></p></note>", 1));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("<note version='1'/><note version='1'/>", 1));
    }

    @Test void externalEntitiesAreRejected() {
        String xml = "<!DOCTYPE note [<!ENTITY stolen SYSTEM 'file:///does-not-exist'>]>"
                + "<note version='1'><p block='NORMAL'><span marks='0'>&stolen;</span></p></note>";
        assertThrows(IllegalArgumentException.class, () -> codec.decode(xml, 1));
    }

    @Test void clipboardControlCharactersAndUnpairedSurrogatesAreReversible() {
        NoteDocument document = NoteDocument.plain("NUL: \u0000 escape: \u001b surrogate: \ud800");
        String encoded = codec.encode(document);
        assertTrue(encoded.contains("utf16-base64"));
        assertEquals(document, codec.decode(encoded, 1));
    }
}
