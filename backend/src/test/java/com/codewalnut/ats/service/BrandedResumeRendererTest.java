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
            "Bengaluru, India", "asha@example.test", "github.com/asha-tester",
            "Final-year B.Tech student who has built three full-stack apps with React, Node.js and PostgreSQL.",
            List.of(new BrandedResume.SkillGroup("Backend / core", List.of("Java", "Spring Boot", "Node.js")),
                    new BrandedResume.SkillGroup("Web", List.of("React", "TypeScript"))),
            List.of(new BrandedResume.Section("Experience", List.of(new BrandedResume.Entry("Full-Stack Developer Intern",
                            "Acme Learning", "Jan 2025 – Jun 2025", List.of("Built course pages in React & Node.js.", "Wrote API tests with Jest.")))),
                    new BrandedResume.Section("Selected projects", List.of(new BrandedResume.Entry("ShopEasy", "React, Node.js, PostgreSQL", "",
                            List.of("Built an online store with cart, payments and an admin dashboard used by 40 test users.")))),
                    new BrandedResume.Section("Education", List.of(new BrandedResume.Entry("B.Tech in Computer Science",
                            "Example Institute of Technology", "2022 – 2026", List.of("CGPA: 8.1/10")))),
                    new BrandedResume.Section("Achievements", List.of(new BrandedResume.Entry("", "", "",
                            List.of("1st place, intercollege CSS contest <with> tricky chars")))),
                    new BrandedResume.Section("Leadership", List.of(new BrandedResume.Entry("Class Representative", "Example Institute of Technology",
                            "2022 – 2026", List.of("Represented a batch of 60 students and coordinated department events."))))));

    private final BrandedResumeRenderer renderer = new BrandedResumeRenderer();
    private final BrandedResumeRenderer.Options options = new BrandedResumeRenderer.Options(true,
            List.of("Java basics test: 80% (pass mark 60%)"), "Presented by Code Walnut | Staffing enquiries through Code Walnut");

    @Test
    void pdfFollowsTheTemplateWithEmailAndLink() throws Exception {
        byte[] pdf = renderer.pdf(SAMPLE, options);
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        String text;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            text = new PDFTextStripper().getText(doc);
        }
        assertThat(text).contains("CODE WALNUT / TALENT PROFILE", "Asha Tester", "Full Stack Developer",
                "Bengaluru, India | asha@example.test | github.com/asha-tester", "TECHNICAL SKILLS", "CODE WALNUT SCREENING",
                "Java basics test: 80%", "Full-Stack Developer Intern | Acme Learning / Jan 2025 – Jun 2025",
                "ShopEasy | React, Node.js, PostgreSQL", "SELECTED PROJECTS", "B.Tech in Computer Science / 2022 – 2026",
                "Class Representative Example Institute of Technology | 2022 – 2026. Represented",
                "Presented by Code Walnut | Staffing enquiries through Code Walnut", "01", "<with>")
                .doesNotContain("SUMMARY");
        dump("codewalnut-resume.pdf", pdf);
    }

    @Test
    void aLongResumeFlowsOntoNumberedPages() throws Exception {
        List<BrandedResume.Entry> jobs = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            jobs.add(new BrandedResume.Entry("Software Engineer " + i, "Example Corp " + i, "2015 – 2016",
                    List.of("Built and ran services for a large retail client with Java and Spring Boot.",
                            "Reviewed code, mentored two juniors and improved the build time by a third.",
                            "Worked with product owners on requirements and release planning.")));
        }
        BrandedResume longer = new BrandedResume(SAMPLE.name(), SAMPLE.headline(), SAMPLE.location(), SAMPLE.email(), SAMPLE.link(),
                SAMPLE.summary(), SAMPLE.skills(), List.of(new BrandedResume.Section("Experience", jobs)));
        byte[] pdf = renderer.pdf(longer, options);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(2);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(2);
            assertThat(stripper.getText(doc)).contains("Software Engineer 9", "02", "Presented by Code Walnut");
        }
        dump("codewalnut-resume-long.pdf", pdf);
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
        assertThat(text).contains("Asha Tester", "Bengaluru, India | asha@example.test | github.com/asha-tester", "Built course pages in React & Node.js.")
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
