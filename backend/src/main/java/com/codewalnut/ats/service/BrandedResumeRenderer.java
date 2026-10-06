package com.codewalnut.ats.service;

import com.codewalnut.ats.client.BrandedResume;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Turns a {@link BrandedResume} into CodeWalnut-branded files (ADR-0012): a PDF for clients
 * (HTML + CSS through openhtmltopdf, Liberation Sans embedded) and an editable Word file.
 */
@Component
public class BrandedResumeRenderer {

    /** showEmail: print the candidate's email under their name. screening: lines for "CodeWalnut screening". */
    public record Options(boolean showEmail, List<String> screening, String footer) {}

    // Colours from CodeWalnut's résumé template.
    private static final String TEXT = "293140";
    private static final String GREY = "606B7A";
    private static final String ACCENT = "4459EB";
    private static final String RULE = "DCE1EB";

    private final byte[] logo = resource("branding/codewalnut-logo.png");
    private final byte[] regular = resource("branding/LiberationSans-Regular.ttf");
    private final byte[] bold = resource("branding/LiberationSans-Bold.ttf");

    // ---- PDF ----

    public byte[] pdf(BrandedResume r, Options o) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(() -> new java.io.ByteArrayInputStream(regular), "Liberation Sans", 400,
                    com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.useFont(() -> new java.io.ByteArrayInputStream(bold), "Liberation Sans", 700,
                    com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.withHtmlContent(html(r, o), null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String html(BrandedResume r, Options o) {
        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"/><style>")
                .append("@page { size: A4; margin: 46pt 0 52pt 0;")
                .append(" @bottom-center { content: element(footer); vertical-align: top; } }")
                .append("@page :first { margin-top: 0; }")
                .append("body { font-family: 'Liberation Sans'; font-size: 9pt; line-height: 1.2; color: #").append(TEXT).append("; margin: 0; }")
                .append(".bar { height: 6.75pt; background: #").append(ACCENT).append("; margin: 0 0 50pt 0; }")
                .append(".page { padding: 0 40pt; }")
                .append(".top { width: 100%; border-collapse: collapse; } .top td { vertical-align: top; padding: 0; }")
                .append(".kicker { font-size: 7.5pt; font-weight: bold; color: #").append(GREY).append("; letter-spacing: 0.2pt; }")
                .append("h1 { font-size: 26pt; line-height: 1.1; margin: 8pt 0 0 0; color: #").append(TEXT).append("; }")
                .append(".headline { font-size: 11pt; color: #").append(GREY).append("; margin-top: 4pt; }")
                .append(".contact { font-size: 8pt; color: #").append(GREY).append("; margin-top: 3pt; }")
                .append(".link { color: #").append(ACCENT).append("; }")
                .append(".logo { width: 105pt; } .summary { font-size: 10pt; line-height: 1.17; margin: 12pt 0 0 0; }")
                .append(".rule { border-top: 1pt solid #").append(RULE).append("; margin-top: 10pt; }")
                .append("h2 { font-size: 8pt; font-weight: bold; color: #").append(ACCENT)
                .append("; letter-spacing: 0.2pt; margin: 8pt 0 6pt 0; page-break-after: avoid; }")
                .append(".skill { margin: 0 0 5pt 0; } .b { font-weight: bold; } .grey { color: #").append(GREY).append("; }")
                .append(".entry { margin: 0 0 6pt 0; page-break-inside: avoid; } .etitle { font-size: 9.5pt; }")
                .append(".etitle .b { font-weight: bold; } .sub { color: #").append(GREY).append("; margin-top: 2pt; }")
                .append(".para { margin: 3pt 0 0 0; line-height: 1.12; }")
                .append("ul { margin: 3pt 0 0 0; padding: 0; list-style: none; }")
                .append("li { margin: 0 0 4pt 0; padding-left: 7pt; text-indent: -7pt; line-height: 1.12; }")
                .append(".footer { position: running(footer); width: 515pt; margin-left: 40pt; border-top: 1pt solid #").append(RULE)
                .append("; padding-top: 6pt; font-size: 7.5pt; color: #").append(GREY).append("; }")
                .append(".footer table { width: 100%; border-collapse: collapse; } .footer td { padding: 0; }")
                .append(".pageno::after { content: counter(page, decimal-leading-zero); }")
                .append("</style></head><body>");
        h.append("<div class=\"footer\"><table><tr><td>").append(esc(o.footer()))
                .append("</td><td style=\"text-align: right;\"><span class=\"pageno\"></span></td></tr></table></div>");
        h.append("<div class=\"bar\"></div><div class=\"page\">");
        h.append("<table class=\"top\"><tr><td><div class=\"kicker\">CODE WALNUT / TALENT PROFILE</div><h1>")
                .append(esc(r.name())).append("</h1>");
        if (StringUtils.hasText(r.headline())) {
            h.append("<div class=\"headline\">").append(esc(r.headline())).append("</div>");
        }
        List<String> contact = contactParts(r, o);
        if (!contact.isEmpty()) {
            h.append("<div class=\"contact\">");
            for (int i = 0; i < contact.size(); i++) {
                String part = contact.get(i);
                h.append(i == 0 ? "" : " | ");
                boolean isLink = StringUtils.hasText(r.link()) && part.equals(r.link().strip());
                h.append(isLink ? "<span class=\"link\">" + esc(part) + "</span>" : esc(part));
            }
            h.append("</div>");
        }
        h.append("</td><td style=\"text-align: right; width: 110pt;\"><img class=\"logo\" src=\"data:image/png;base64,")
                .append(Base64.getEncoder().encodeToString(logo)).append("\"/></td></tr></table>");
        if (StringUtils.hasText(r.summary())) {
            h.append("<p class=\"summary\">").append(esc(r.summary())).append("</p>");
        }
        List<BrandedResume.SkillGroup> skills = nonEmptySkills(r);
        if (!skills.isEmpty()) {
            h.append("<div class=\"rule\"></div><h2>TECHNICAL SKILLS</h2>");
            for (BrandedResume.SkillGroup g : skills) {
                h.append("<div class=\"skill\">");
                if (StringUtils.hasText(g.label())) {
                    h.append("<span class=\"b\">").append(esc(g.label().strip())).append("</span> ");
                }
                h.append(esc(String.join(", ", g.items()))).append("</div>");
            }
        }
        if (o.screening() != null && !o.screening().isEmpty()) {
            h.append("<div class=\"rule\"></div><h2>CODE WALNUT SCREENING</h2><ul>");
            o.screening().forEach(line -> h.append("<li>&#8226; ").append(esc(line)).append("</li>"));
            h.append("</ul>");
        }
        for (BrandedResume.Section s : sections(r)) {
            Kind kind = kind(s.title());
            h.append("<div class=\"rule\"></div><h2>").append(esc(s.title().strip().toUpperCase(java.util.Locale.ROOT))).append("</h2>");
            for (BrandedResume.Entry e : s.entries()) {
                List<String> bullets = bullets(e);
                boolean hasTitle = StringUtils.hasText(e.title()) || StringUtils.hasText(e.subtitle());
                h.append("<div class=\"entry\">");
                if (kind == Kind.OTHER && hasTitle && bullets.size() == 1) {
                    // e.g. "Class Representative  Dr Ambedkar Institute of Technology | 2022 - 2026. Represented a batch of…"
                    h.append("<div class=\"para\" style=\"margin-top: 0;\">").append(inline(e)).append(" ")
                            .append(esc(bullets.get(0))).append("</div></div>");
                    continue;
                }
                if (hasTitle || StringUtils.hasText(e.period())) {
                    h.append("<div class=\"etitle\">").append(titleLine(e, kind)).append("</div>");
                }
                if (kind == Kind.EDUCATION && StringUtils.hasText(e.subtitle())) {
                    h.append("<div class=\"sub\">").append(esc(e.subtitle().strip())).append("</div>");
                }
                if (kind == Kind.EXPERIENCE || bullets.size() > 1 && kind != Kind.EDUCATION) {
                    if (!bullets.isEmpty()) {
                        h.append("<ul>");
                        bullets.forEach(b -> h.append("<li>&#8226; ").append(esc(b)).append("</li>"));
                        h.append("</ul>");
                    }
                } else {
                    bullets.forEach(b -> h.append("<div class=\"para\">").append(esc(b)).append("</div>"));
                }
                h.append("</div>");
            }
        }
        h.append("</div></body></html>");
        return h.toString();
    }

    enum Kind { EXPERIENCE, PROJECTS, EDUCATION, OTHER }

    static Kind kind(String title) {
        String t = title == null ? "" : title.toLowerCase(java.util.Locale.ROOT);
        if (t.contains("project")) {
            return Kind.PROJECTS;
        }
        if (t.contains("education") || t.contains("academic") || t.contains("qualification")) {
            return Kind.EDUCATION;
        }
        if (t.contains("experience") || t.contains("employment") || t.contains("work") || t.contains("internship")) {
            return Kind.EXPERIENCE;
        }
        return Kind.OTHER;
    }

    /** "Role | Organisation / dates" (projects: the stack in grey; education: the institution goes on the next line). */
    private static String titleLine(BrandedResume.Entry e, Kind kind) {
        StringBuilder t = new StringBuilder();
        String title = StringUtils.hasText(e.title()) ? e.title().strip() : "";
        String sub = StringUtils.hasText(e.subtitle()) ? e.subtitle().strip() : "";
        if (kind == Kind.PROJECTS || kind == Kind.EDUCATION) {
            if (!title.isEmpty()) {
                t.append("<span class=\"b\">").append(esc(title)).append("</span>");
            }
            if (kind == Kind.PROJECTS && !sub.isEmpty()) {
                t.append("<span class=\"grey\">").append(title.isEmpty() ? "" : " | ").append(esc(sub)).append("</span>");
            }
            if (kind == Kind.EDUCATION && title.isEmpty() && !sub.isEmpty()) {
                t.append("<span class=\"b\">").append(esc(sub)).append("</span>");
            }
        } else {
            String main = title.isEmpty() || sub.isEmpty() ? title + sub : title + " | " + sub;
            t.append("<span class=\"b\">").append(esc(main)).append("</span>");
        }
        if (StringUtils.hasText(e.period())) {
            t.append("<span class=\"grey\">").append(t.length() > 0 ? " / " : "").append(esc(e.period().strip())).append("</span>");
        }
        return t.toString();
    }

    private static String inline(BrandedResume.Entry e) {
        StringBuilder t = new StringBuilder();
        if (StringUtils.hasText(e.title())) {
            t.append("<span class=\"b\">").append(esc(e.title().strip())).append("</span>");
        }
        String rest = String.join(" | ", java.util.stream.Stream.of(e.subtitle(), e.period())
                .filter(StringUtils::hasText).map(String::strip).toList());
        if (!rest.isEmpty()) {
            t.append(t.length() > 0 ? " " : "").append(esc(rest)).append(".");
        }
        return t.toString();
    }

    // ---- Word (.docx) ----

    public byte[] docx(BrandedResume r, Options o) {
        StringBuilder body = new StringBuilder();
        body.append(para(run("CODE WALNUT / TALENT PROFILE", true, 15, GREY), 0, 60));
        body.append(para(run(r.name(), true, 52, TEXT), 0, 40));
        if (StringUtils.hasText(r.headline())) {
            body.append(para(run(r.headline(), false, 22, GREY), 0, 20));
        }
        String meta = meta(r, o);
        if (!meta.isEmpty()) {
            body.append(para(run(meta, false, 16, GREY), 0, 120));
        }
        if (StringUtils.hasText(r.summary())) {
            body.append(para(run(r.summary(), false, 20, null), 0, 0));
        }
        List<BrandedResume.SkillGroup> skills = nonEmptySkills(r);
        if (!skills.isEmpty()) {
            body.append(heading("Technical skills"));
            for (BrandedResume.SkillGroup g : skills) {
                body.append(para(run(g.label() + "  ", true, 19, null) + run(String.join(", ", g.items()), false, 19, null), 0, 20));
            }
        }
        if (o.screening() != null && !o.screening().isEmpty()) {
            body.append(heading("Code Walnut screening"));
            o.screening().forEach(line -> body.append(bullet(line)));
        }
        for (BrandedResume.Section s : sections(r)) {
            body.append(heading(s.title()));
            for (BrandedResume.Entry e : s.entries()) {
                if (StringUtils.hasText(e.title()) || StringUtils.hasText(e.subtitle()) || StringUtils.hasText(e.period())) {
                    StringBuilder line = new StringBuilder();
                    if (StringUtils.hasText(e.title())) {
                        line.append(run(e.title(), true, 19, null));
                    }
                    if (StringUtils.hasText(e.subtitle())) {
                        line.append(run((StringUtils.hasText(e.title()) ? " | " : "") + e.subtitle(), false, 19, null));
                    }
                    if (StringUtils.hasText(e.period())) {
                        line.append(run(" / " + e.period(), false, 19, GREY));
                    }
                    body.append("<w:p><w:pPr><w:keepNext/><w:tabs><w:tab w:val=\"right\" w:pos=\"9638\"/></w:tabs>"
                            + "<w:spacing w:before=\"100\" w:after=\"20\"/></w:pPr>").append(line).append("</w:p>");
                }
                bullets(e).forEach(b -> body.append(bullet(b)));
            }
        }
        String document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><w:body>" + body
                + "<w:sectPr><w:headerReference w:type=\"default\" r:id=\"rIdHeader\"/>"
                + "<w:footerReference w:type=\"default\" r:id=\"rIdFooter\"/>"
                + "<w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
                + "<w:pgMar w:top=\"1000\" w:right=\"964\" w:bottom=\"1000\" w:left=\"964\" w:header=\"400\" w:footer=\"400\" w:gutter=\"0\"/>"
                + "</w:sectPr></w:body></w:document>";
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "[Content_Types].xml", CONTENT_TYPES);
            put(zip, "_rels/.rels", ROOT_RELS);
            put(zip, "word/document.xml", document);
            put(zip, "word/_rels/document.xml.rels", DOCUMENT_RELS);
            put(zip, "word/styles.xml", STYLES);
            put(zip, "word/numbering.xml", NUMBERING);
            put(zip, "word/header1.xml", HEADER);
            put(zip, "word/_rels/header1.xml.rels", HEADER_RELS);
            put(zip, "word/footer1.xml", footer(o.footer()));
            zip.putNextEntry(new ZipEntry("word/media/logo.png"));
            zip.write(logo);
            zip.closeEntry();
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String heading(String text) {
        return "<w:p><w:pPr><w:keepNext/><w:pBdr><w:top w:val=\"single\" w:sz=\"8\" w:space=\"10\" w:color=\"" + RULE
                + "\"/></w:pBdr><w:spacing w:before=\"220\" w:after=\"100\"/></w:pPr>"
                + run(text.toUpperCase(java.util.Locale.ROOT), true, 16, ACCENT) + "</w:p>";
    }

    private static String bullet(String text) {
        return "<w:p><w:pPr><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr>"
                + "<w:spacing w:before=\"0\" w:after=\"20\"/></w:pPr>" + run(text, false, 19, null) + "</w:p>";
    }

    private static String para(String runs, int before, int after) {
        return "<w:p><w:pPr><w:spacing w:before=\"" + before + "\" w:after=\"" + after + "\"/></w:pPr>" + runs + "</w:p>";
    }

    /** size in half-points. */
    private static String run(String text, boolean isBold, int size, String color) {
        return "<w:r><w:rPr>" + (isBold ? "<w:b/>" : "") + (color != null ? "<w:color w:val=\"" + color + "\"/>" : "")
                + "<w:sz w:val=\"" + size + "\"/></w:rPr><w:t xml:space=\"preserve\">" + esc(text) + "</w:t></w:r>";
    }

    private static String footer(String text) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:ftr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr>"
                + run(text, false, 16, GREY) + "</w:p></w:ftr>";
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    // ---- shared ----

    /** City, email and profile link, in that order, for the line under the title. */
    private static List<String> contactParts(BrandedResume r, Options o) {
        return java.util.stream.Stream.of(r.location(), o.showEmail() ? r.email() : null, r.link())
                .filter(StringUtils::hasText).map(String::strip).toList();
    }

    private static String meta(BrandedResume r, Options o) {
        return String.join(" | ", contactParts(r, o));
    }

    private static List<BrandedResume.SkillGroup> nonEmptySkills(BrandedResume r) {
        return r.skills() == null ? List.of() : r.skills().stream()
                .filter(g -> g != null && g.items() != null && g.items().stream().anyMatch(StringUtils::hasText))
                .map(g -> new BrandedResume.SkillGroup(g.label() == null ? "" : g.label(),
                        g.items().stream().filter(StringUtils::hasText).map(String::strip).toList()))
                .toList();
    }

    private static List<BrandedResume.Section> sections(BrandedResume r) {
        return r.sections() == null ? List.of() : r.sections().stream()
                .filter(s -> s != null && StringUtils.hasText(s.title()) && s.entries() != null && !s.entries().isEmpty())
                .toList();
    }

    private static List<String> bullets(BrandedResume.Entry e) {
        return e.bullets() == null ? List.of() : e.bullets().stream().filter(StringUtils::hasText).map(String::strip).toList();
    }

    /** XML-escape, dropping characters XML can't hold. */
    static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> {
                    if (c >= 0x20 || c == '\t' || c == '\n') {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static byte[] resource(String path) {
        try (InputStream in = BrandedResumeRenderer.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final String CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Default Extension=\"png\" ContentType=\"image/png\"/>"
            + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
            + "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"
            + "<Override PartName=\"/word/numbering.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/>"
            + "<Override PartName=\"/word/header1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml\"/>"
            + "<Override PartName=\"/word/footer1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml\"/>"
            + "</Types>";

    private static final String ROOT_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
            + "</Relationships>";

    private static final String DOCUMENT_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
            + "<Relationship Id=\"rIdNumbering\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering\" Target=\"numbering.xml\"/>"
            + "<Relationship Id=\"rIdHeader\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/header\" Target=\"header1.xml\"/>"
            + "<Relationship Id=\"rIdFooter\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer\" Target=\"footer1.xml\"/>"
            + "</Relationships>";

    private static final String HEADER_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rIdLogo\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/logo.png\"/>"
            + "</Relationships>";

    private static final String STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:eastAsia=\"Arial\" w:cs=\"Arial\"/>"
            + "<w:color w:val=\"293140\"/><w:sz w:val=\"19\"/><w:szCs w:val=\"19\"/><w:lang w:val=\"en-IN\"/></w:rPr></w:rPrDefault>"
            + "<w:pPrDefault><w:pPr><w:spacing w:after=\"40\" w:line=\"264\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>"
            + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>"
            + "</w:styles>";

    private static final String NUMBERING = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:numbering xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:abstractNum w:abstractNumId=\"0\"><w:multiLevelType w:val=\"singleLevel\"/>"
            + "<w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"bullet\"/><w:lvlText w:val=\"•\"/><w:lvlJc w:val=\"left\"/>"
            + "<w:pPr><w:ind w:left=\"340\" w:hanging=\"220\"/></w:pPr></w:lvl></w:abstractNum>"
            + "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num></w:numbering>";

    /** The logo (924 × 432 px) at 105 pt wide, right-aligned in the page header, as in the template. */
    private static final String HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:hdr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" "
            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" "
            + "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" "
            + "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" "
            + "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
            + "<w:p><w:pPr><w:jc w:val=\"right\"/></w:pPr><w:r><w:drawing>"
            + "<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"1333500\" cy=\"623454\"/>"
            + "<wp:docPr id=\"1\" name=\"CodeWalnut\"/><a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
            + "<pic:pic><pic:nvPicPr><pic:cNvPr id=\"1\" name=\"logo.png\"/><pic:cNvPicPr/></pic:nvPicPr>"
            + "<pic:blipFill><a:blip r:embed=\"rIdLogo\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>"
            + "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"1333500\" cy=\"623454\"/></a:xfrm>"
            + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>"
            + "</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p></w:hdr>";
}
