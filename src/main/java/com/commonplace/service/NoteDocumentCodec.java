package com.commonplace.service;

import com.commonplace.model.NoteDocument;
import com.commonplace.model.NoteDocument.Block;
import com.commonplace.model.NoteDocument.Paragraph;
import com.commonplace.model.NoteDocument.Span;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

/** Stable UTF-8 XML, independent of RichTextFX and Java serialization. */
public final class NoteDocumentCodec {
    public String encode(NoteDocument document) {
        try {
            StringWriter output = new StringWriter();
            var writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeStartElement("note");
            writer.writeAttribute("version", String.valueOf(NoteDocument.FORMAT_VERSION));
            for (Paragraph paragraph : document.paragraphs()) {
                writer.writeStartElement("p");
                writer.writeAttribute("block", paragraph.block().name());
                for (Span span : paragraph.spans()) {
                    writer.writeStartElement("span");
                    writer.writeAttribute("marks", String.valueOf(span.marks()));
                    if (needsEncoding(span.text())) {
                        // Clipboard/code text can contain characters that XML 1.0 cannot represent.
                        writer.writeAttribute("encoding", "utf16-base64");
                        byte[] bytes = new byte[span.text().length() * 2];
                        for (int i = 0; i < span.text().length(); i++) {
                            bytes[i * 2] = (byte) (span.text().charAt(i) >>> 8);
                            bytes[i * 2 + 1] = (byte) span.text().charAt(i);
                        }
                        writer.writeCharacters(Base64.getEncoder().encodeToString(bytes));
                    } else writer.writeCharacters(span.text());
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.close();
            return output.toString();
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException("Could not encode the note document.", e);
        }
    }

    public NoteDocument decode(String content, int version) {
        if (version != NoteDocument.FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported note format version: " + version);
        }
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
            var reader = factory.createXMLStreamReader(new StringReader(content));
            try {
                reader.nextTag();
                require(reader.getLocalName().equals("note")
                        && String.valueOf(version).equals(reader.getAttributeValue(null, "version")));
                List<Paragraph> paragraphs = new ArrayList<>();
                while (reader.nextTag() == XMLStreamConstants.START_ELEMENT) {
                    require(reader.getLocalName().equals("p"));
                    Block block = Block.valueOf(reader.getAttributeValue(null, "block"));
                    List<Span> spans = new ArrayList<>();
                    while (reader.nextTag() == XMLStreamConstants.START_ELEMENT) {
                        require(reader.getLocalName().equals("span"));
                        int marks = Integer.parseInt(reader.getAttributeValue(null, "marks"));
                        String encoding = reader.getAttributeValue(null, "encoding");
                        String text = reader.getElementText();
                        if (encoding != null) {
                            require(encoding.equals("utf16-base64"));
                            byte[] bytes = Base64.getDecoder().decode(text);
                            require(bytes.length % 2 == 0);
                            StringBuilder decoded = new StringBuilder(bytes.length / 2);
                            for (int i = 0; i < bytes.length; i += 2) {
                                decoded.append((char) ((bytes[i] & 0xff) << 8 | (bytes[i + 1] & 0xff)));
                            }
                            text = decoded.toString();
                        }
                        spans.add(new Span(text, marks));
                    }
                    require(reader.getLocalName().equals("p"));
                    paragraphs.add(new Paragraph(block, spans));
                }
                require(reader.getLocalName().equals("note"));
                while (reader.hasNext()) {
                    int next = reader.next();
                    require(next == XMLStreamConstants.END_DOCUMENT
                            || (next == XMLStreamConstants.CHARACTERS && reader.isWhiteSpace()));
                }
                return new NoteDocument(paragraphs);
            } finally { reader.close(); }
        } catch (XMLStreamException | IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("The saved note could not be read. Its content has been retained.", e);
        }
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Invalid note document.");
    }

    private static boolean needsEncoding(String text) {
        return text.codePoints().anyMatch(code -> code != 9 && !(code >= 0x20 && code <= 0xd7ff)
                && !(code >= 0xe000 && code <= 0xfffd) && !(code >= 0x10000 && code <= 0x10ffff));
    }
}
