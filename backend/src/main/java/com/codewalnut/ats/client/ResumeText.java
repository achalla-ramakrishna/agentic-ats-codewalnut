package com.codewalnut.ats.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Plain text out of résumé files, without extra libraries. */
public final class ResumeText {

    static final int MAX_CHARS = 60_000;
    private static final int MAX_XML_BYTES = 20 * 1024 * 1024;

    private ResumeText() {}

    public static boolean isPdf(ResumeAnalyzer.ResumeFile file) {
        return startsWith(file.data(), "%PDF");
    }

    public static boolean isDocx(ResumeAnalyzer.ResumeFile file) {
        return startsWith(file.data(), "PK\u0003\u0004");
    }

    /** Text of a Word (.docx) file: paragraphs on their own lines. */
    public static String docx(byte[] data) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    byte[] xml = zip.readNBytes(MAX_XML_BYTES);
                    return clip(xmlText(new String(xml, StandardCharsets.UTF_8)));
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new CalendarException("This Word file couldn't be opened. Please upload it as a PDF.");
        }
        throw new CalendarException("This Word file has no text. Please upload it as a PDF.");
    }

    static String xmlText(String xml) {
        String text = xml
                .replaceAll("</w:p>", "\n")
                .replaceAll("<w:(tab|br)\\b[^>]*/>", " ")
                .replaceAll("<[^>]+>", "");
        return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&amp;", "&")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n\\s*\\n+", "\n")
                .strip();
    }

    private static final Pattern STREAM = Pattern.compile("stream\\r?\\n(.*?)\\r?\\nendstream", Pattern.DOTALL);
    private static final Pattern SHOWN = Pattern.compile("\\((?:\\\\.|[^\\\\)])*\\)\\s*'|\\((?:\\\\.|[^\\\\)])*\\)\\s*Tj|\\[(.*?)\\]\\s*TJ|(T\\*|ET|Td|TD)", Pattern.DOTALL);
    private static final Pattern STRING = Pattern.compile("\\(((?:\\\\.|[^\\\\)])*)\\)", Pattern.DOTALL);

    /**
     * A best-effort reading of simple PDFs (text shown with standard fonts), for the offline
     * stand-in only. Real résumés go to Claude as PDFs, which reads them properly.
     */
    public static String pdfBestEffort(byte[] data) {
        String raw = new String(data, StandardCharsets.ISO_8859_1);
        StringBuilder out = new StringBuilder();
        Matcher streams = STREAM.matcher(raw);
        while (streams.find() && out.length() < MAX_CHARS) {
            byte[] bytes = streams.group(1).getBytes(StandardCharsets.ISO_8859_1);
            String content = inflate(bytes);
            appendShownText(content != null ? content : streams.group(1), out);
        }
        return clip(out.toString().replaceAll("[ \\t]+", " ").replaceAll("\\n\\s*\\n+", "\n").strip());
    }

    private static void appendShownText(String content, StringBuilder out) {
        Matcher m = SHOWN.matcher(content);
        while (m.find()) {
            String op = m.group(0);
            if (m.group(2) != null) {
                out.append('\n');
                continue;
            }
            Matcher s = STRING.matcher(m.group(1) != null ? m.group(1) : op);
            while (s.find()) {
                out.append(unescape(s.group(1)));
            }
            if (op.endsWith("'")) {
                out.append('\n');
            }
        }
    }

    private static String unescape(String s) {
        return s.replace("\\(", "(").replace("\\)", ")").replace("\\n", "\n").replace("\\\\", "\\");
    }

    private static String inflate(byte[] bytes) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(bytes);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!inflater.finished() && out.size() < MAX_XML_BYTES) {
                int n = inflater.inflate(buffer);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                out.write(buffer, 0, n);
            }
            return out.size() == 0 ? null : out.toString(StandardCharsets.ISO_8859_1);
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
    }

    private static String clip(String text) {
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }

    private static boolean startsWith(byte[] data, String prefix) {
        byte[] p = prefix.getBytes(StandardCharsets.ISO_8859_1);
        if (data == null || data.length < p.length) {
            return false;
        }
        for (int i = 0; i < p.length; i++) {
            if (data[i] != p[i]) {
                return false;
            }
        }
        return true;
    }
}
