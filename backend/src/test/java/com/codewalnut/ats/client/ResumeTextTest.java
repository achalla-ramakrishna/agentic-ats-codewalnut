package com.codewalnut.ats.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/** Text out of résumé files (fake résumés only). */
class ResumeTextTest {

    /** A minimal .docx whose body is the given WordprocessingML paragraphs. */
    static byte[] docx(String paragraphs) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("<w:document xmlns:w=\"x\"><w:body>" + paragraphs + "</w:body></w:document>")
                    .getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    /** A minimal PDF showing each line with Tj, its content stream compressed like real PDFs. */
    static byte[] pdf(String... lines) {
        StringBuilder content = new StringBuilder("BT /F1 12 Tf 72 720 Td\n");
        for (String line : lines) {
            content.append('(').append(line.replace("(", "\\(").replace(")", "\\)")).append(") Tj 0 -14 Td\n");
        }
        content.append("ET");
        byte[] raw = content.toString().getBytes(StandardCharsets.ISO_8859_1);
        Deflater deflater = new Deflater();
        deflater.setInput(raw);
        deflater.finish();
        byte[] buffer = new byte[raw.length + 64];
        int n = deflater.deflate(buffer);
        deflater.end();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(("%PDF-1.4\n1 0 obj << /Length " + n + " /Filter /FlateDecode >>\nstream\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        out.write(buffer, 0, n);
        out.writeBytes("\nendstream\nendobj\n%%EOF".getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    @Test
    void readsWordParagraphsAndEntities() throws IOException {
        String text = ResumeText.docx(docx("<w:p><w:r><w:t>Meera Test</w:t></w:r></w:p>"
                + "<w:p><w:r><w:t xml:space=\"preserve\">Skills: </w:t></w:r><w:r><w:t>C++ &lt;and&gt; Go</w:t></w:r></w:p>"));
        assertThat(text).isEqualTo("Meera Test\nSkills: C++ <and> Go");
    }

    @Test
    void readsSimpleCompressedPdfs() {
        String text = ResumeText.pdfBestEffort(pdf("Kiran Test", "kiran@example.test", "Java (Spring Boot)"));
        assertThat(text).contains("Kiran Test").contains("kiran@example.test").contains("Java (Spring Boot)");
        assertThat(text.lines()).contains("Kiran Test");
    }
}
