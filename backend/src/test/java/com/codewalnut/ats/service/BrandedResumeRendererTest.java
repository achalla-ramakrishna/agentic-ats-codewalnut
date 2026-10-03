package com.codewalnut.ats.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewalnut.ats.client.BrandedResume;
import com.codewalnut.ats.client.ResumeText;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** CodeWalnut résumé files (fake candidate). */
class BrandedResumeRendererTest {

    static final BrandedResume SAMPLE = new BrandedResume("Asha Tester", "Full Stack Developer | MERN / PERN",
            "Bengaluru, India", "asha@example.test",
            "Final-year B.Tech student who has built three full-stack apps with React, Node.js and PostgreSQL.",
            List.of(new BrandedResume.SkillGroup("Backend / core", List.of("Java", "Spring Boot", "Node.js")),
                    new BrandedResume.SkillGroup("Web", List.of("React", "TypeScript"))),
            List.of(new BrandedResume.Section("Experience", List.of(new BrandedResume.Entry("Full-Stack Developer Intern",
                            "Acme Learning", "Jan 2025 – Jun 2025", List.of("Built course pages in React & Node.js.", "Wrote API tests with Jest.")))),
                    new BrandedResume.Section("Education", List.of(new BrandedResume.Entry("B.Tech in Computer Science",
                            "Example Institute of Technology", "2022 – 2026", List.of("CGPA: 8.1/10")))),
                    new BrandedResume.Section("Achievements", List.of(new BrandedResume.Entry("", "", "",
                            List.of("1st place, intercollege CSS contest <with> tricky chars"))))));

    private final BrandedResumeRenderer renderer = new BrandedResumeRenderer();
    private final BrandedResumeRenderer.Options options = new BrandedResumeRenderer.Options(false,
            List.of("Java basics test: 80% (pass mark 60%)"), "Presented by CodeWalnut | Staffing enquiries through CodeWalnut");

    @Test
    void pdfHasTheBrandingContentAndNoEmailWhenHidden() throws Exception {
        byte[] pdf = renderer.pdf(SAMPLE, options);
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        String text;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            text = new PDFTextStripper().getText(doc);
        }
        assertThat(text).contains("Asha Tester", "Full Stack Developer", "SUMMARY", "CODEWALNUT SCREENING",
                "Java basics test: 80%", "Jan 2025 – Jun 2025", "Presented by CodeWalnut", "<with>")
                .doesNotContain("asha@example.test");
        dump("codewalnut-resume.pdf", pdf);
    }

    @Test
    void wordFileOpensWithLogoHeaderAndBullets() throws Exception {
        byte[] docx = renderer.docx(SAMPLE, new BrandedResumeRenderer.Options(true, List.of(), "Presented by CodeWalnut"));
        List<String> parts = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                parts.add(e.getName());
            }
        }
        assertThat(parts).contains("word/document.xml", "word/header1.xml", "word/media/logo.png", "word/numbering.xml");
        String text = ResumeText.docx(docx);
        assertThat(text).contains("Asha Tester", "Bengaluru, India | asha@example.test", "Built course pages in React & Node.js.")
                .doesNotContain("CODEWALNUT SCREENING");
        dump("codewalnut-resume.docx", docx);
    }

    /** Set -Dresume.dump=/some/dir to look at the files. */
    private static void dump(String name, byte[] bytes) throws Exception {
        String dir = System.getProperty("resume.dump");
        if (dir != null) {
            Files.write(Path.of(dir, name), bytes);
        }
    }
}
