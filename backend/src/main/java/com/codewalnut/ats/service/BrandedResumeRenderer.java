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

    private static final String NAVY = "1E1B4B";
    private static final String ACCENT = "4F5BD5";

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
                .append("@page { size: A4; margin: 16mm 17mm 18mm 17mm; @bottom-center { content: element(footer); } }")
                .append("body { font-family: 'Liberation Sans'; font-size: 9.6pt; line-height: 1.35; color: #1f2937; }")
                .append(".top { width: 100%; border-collapse: collapse; } .top td { vertical-align: top; padding: 0; }")
                .append(".logo { width: 34mm; } h1 { font-size: 21pt; margin: 0; color: #").append(NAVY).append("; }")
                .append(".headline { font-size: 11pt; margin-top: 2pt; } .meta { color: #4b5563; margin-top: 2pt; }")
                .append("h2 { font-size: 10.5pt; text-transform: uppercase; letter-spacing: 0.5pt; color: #").append(NAVY)
                .append("; border-bottom: 1.2pt solid #").append(ACCENT).append("; padding-bottom: 2pt; margin: 11pt 0 4pt; }")
                .append(".summary { margin: 0; } .skills td { padding: 1pt 8pt 1pt 0; vertical-align: top; }")
                .append(".label { font-weight: bold; white-space: nowrap; } .entry { margin-top: 5pt; page-break-inside: avoid; }")
                .append(".etitle { font-weight: bold; } .period { float: right; color: #6b7280; }")
                .append("ul { margin: 2pt 0 0 0; padding-left: 13pt; } li { margin: 1pt 0; }")
                .append(".screen { background: #eef0fb; padding: 5pt 7pt; }")
                .append(".footer { position: running(footer); font-size: 7.8pt; color: #6b7280; text-align: center; }")
                .append("</style></head><body>");
        h.append("<div class=\"footer\">").append(esc(o.footer())).append("</div>");
        h.append("<table class=\"top\"><tr><td><h1>").append(esc(r.name())).append("</h1>");
        if (StringUtils.hasText(r.headline())) {
            h.append("<div class=\"headline\">").append(esc(r.headline())).append("</div>");
        }
        String meta = meta(r, o);
        if (!meta.isEmpty()) {
            h.append("<div class=\"meta\">").append(esc(meta)).append("</div>");
        }
        h.append("</td><td style=\"text-align: right; width: 36mm;\"><img class=\"logo\" src=\"data:image/png;base64,")
                .append(Base64.getEncoder().encodeToString(logo)).append("\"/></td></tr></table>");
        if (StringUtils.hasText(r.summary())) {
            h.append("<h2>Summary</h2><p class=\"summary\">").append(esc(r.summary())).append("</p>");
        }
        List<BrandedResume.SkillGroup> skills = nonEmptySkills(r);
        if (!skills.isEmpty()) {
            h.append("<h2>Technical skills</h2><table class=\"skills\">");
            for (BrandedResume.SkillGroup g : skills) {
                h.append("<tr><td class=\"label\">").append(esc(g.label())).append("</td><td>")
                        .append(esc(String.join(", ", g.items()))).append("</td></tr>");
            }
            h.append("</table>");
        }
        if (o.screening() != null && !o.screening().isEmpty()) {
            h.append("<h2>CodeWalnut screening</h2><div class=\"screen\"><ul>");
            o.screening().forEach(line -> h.append("<li>").append(esc(line)).append("</li>"));
            h.append("</ul></div>");
        }
        for (BrandedResume.Section s : sections(r)) {
            h.append("<h2>").append(esc(s.title())).append("</h2>");
            for (BrandedResume.Entry e : s.entries()) {
                h.append("<div class=\"entry\">");
                if (StringUtils.hasText(e.period())) {
                    h.append("<span class=\"period\">").append(esc(e.period())).append("</span>");
                }
                if (StringUtils.hasText(e.title()) || StringUtils.hasText(e.subtitle())) {
                    h.append("<div>");
                    if (StringUtils.hasText(e.title())) {
                        h.append("<span class=\"etitle\">").append(esc(e.title())).append("</span>");
                    }
                    if (StringUtils.hasText(e.subtitle())) {
                        h.append(StringUtils.hasText(e.title()) ? " | " : "").append(esc(e.subtitle()));
                    }
                    h.append("</div>");
                }
                List<String> bullets = bullets(e);
                if (!bullets.isEmpty()) {
                    h.append("<ul>");
                    bullets.forEach(b -> h.append("<li>").append(esc(b)).append("</li>"));
                    h.append("</ul>");
                }
                h.append("</div>");
            }
        }
        h.append("</body></html>");
        return h.toString();
    }

    // ---- Word (.docx) ----

    public byte[] docx(BrandedResume r, Options o) {
        StringBuilder body = new StringBuilder();
        body.append(para(run(r.name(), true, 42, NAVY), 0, 0));
        if (StringUtils.hasText(r.headline())) {
            body.append(para(run(r.headline(), false, 22, null), 0, 20));
        }
        String meta = meta(r, o);
        if (!meta.isEmpty()) {
            body.append(para(run(meta, false, 19, "4B5563"), 0, 40));
        }
        if (StringUtils.hasText(r.summary())) {
            body.append(heading("Summary")).append(para(run(r.summary(), false, 19, null), 0, 0));
        }
        List<BrandedResume.SkillGroup> skills = nonEmptySkills(r);
        if (!skills.isEmpty()) {
            body.append(heading("Technical skills"));
            for (BrandedResume.SkillGroup g : skills) {
                body.append(para(run(g.label() + "  ", true, 19, null) + run(String.join(", ", g.items()), false, 19, null), 0, 20));
            }
        }
        if (o.screening() != null && !o.screening().isEmpty()) {
            body.append(heading("CodeWalnut screening"));
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
                        line.append("<w:r><w:tab/></w:r>").append(run(e.period(), false, 19, "6B7280"));
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
        return "<w:p><w:pPr><w:keepNext/><w:pBdr><w:bottom w:val=\"single\" w:sz=\"8\" w:space=\"1\" w:color=\"" + ACCENT
                + "\"/></w:pBdr><w:spacing w:before=\"220\" w:after=\"80\"/></w:pPr>"
                + run(text.toUpperCase(java.util.Locale.ROOT), true, 21, NAVY) + "</w:p>";
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
                + run(text, false, 16, "6B7280") + "</w:p></w:ftr>";
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    // ---- shared ----

    private static String meta(BrandedResume r, Options o) {
        String location = StringUtils.hasText(r.location()) ? r.location().strip() : "";
        String email = o.showEmail() && StringUtils.hasText(r.email()) ? r.email().strip() : "";
        return location.isEmpty() || email.isEmpty() ? location + email : location + " | " + email;
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
            + "<w:color w:val=\"1F2937\"/><w:sz w:val=\"19\"/><w:szCs w:val=\"19\"/><w:lang w:val=\"en-IN\"/></w:rPr></w:rPrDefault>"
            + "<w:pPrDefault><w:pPr><w:spacing w:after=\"40\" w:line=\"264\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>"
            + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>"
            + "</w:styles>";

    private static final String NUMBERING = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:numbering xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:abstractNum w:abstractNumId=\"0\"><w:multiLevelType w:val=\"singleLevel\"/>"
            + "<w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"bullet\"/><w:lvlText w:val=\"•\"/><w:lvlJc w:val=\"left\"/>"
            + "<w:pPr><w:ind w:left=\"340\" w:hanging=\"220\"/></w:pPr></w:lvl></w:abstractNum>"
            + "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num></w:numbering>";

    /** The logo (277 × 106 px) at about 3.4 cm wide, right-aligned in the page header. */
    private static final String HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:hdr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" "
            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" "
            + "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" "
            + "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" "
            + "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
            + "<w:p><w:pPr><w:jc w:val=\"right\"/></w:pPr><w:r><w:drawing>"
            + "<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"1224000\" cy=\"468000\"/>"
            + "<wp:docPr id=\"1\" name=\"CodeWalnut\"/><a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
            + "<pic:pic><pic:nvPicPr><pic:cNvPr id=\"1\" name=\"logo.png\"/><pic:cNvPicPr/></pic:nvPicPr>"
            + "<pic:blipFill><a:blip r:embed=\"rIdLogo\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>"
            + "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"1224000\" cy=\"468000\"/></a:xfrm>"
            + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>"
            + "</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p></w:hdr>";
}
