package com.codewalnut.ats.naukri;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Reads the first sheet of an .xlsx file (or a .csv) into rows of text, without a spreadsheet
 * library: an .xlsx is a zip of XML parts. Enough for job-portal exports such as Naukri's
 * (ADR-0026). Limits guard against oversized or hostile files.
 */
public final class SpreadsheetReader {

    static final int MAX_ROWS = 3000;
    static final int MAX_COLUMNS = 80;
    private static final long MAX_PART_BYTES = 30L * 1024 * 1024;

    private SpreadsheetReader() {}

    public static List<List<String>> read(String fileName, byte[] data) {
        String name = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".csv")) {
            return csv(new String(data, StandardCharsets.UTF_8));
        }
        if (name.endsWith(".xls")) {
            throw new IllegalArgumentException("Old Excel (.xls) files can't be read. Open it in Excel and save as .xlsx, then upload again.");
        }
        if (data.length < 4 || data[0] != 'P' || data[1] != 'K') {
            throw new IllegalArgumentException("This isn't an Excel (.xlsx) or CSV file.");
        }
        return xlsx(data);
    }

    // ---- xlsx ----

    static List<List<String>> xlsx(byte[] data) {
        Map<String, byte[]> parts = new HashMap<>();
        // Through the central directory (ZipFile), not ZipInputStream: Naukri's exports carry wrong sizes in
        // their local headers, which the streaming reader rejects.
        java.nio.file.Path tmp = null;
        try {
            tmp = java.nio.file.Files.createTempFile("sheet-", ".xlsx");
            java.nio.file.Files.write(tmp, data);
            try (ZipFile zip = new ZipFile(tmp.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String n = entry.getName();
                    if (n.equals("xl/sharedStrings.xml") || n.equals("xl/workbook.xml") || n.equals("xl/_rels/workbook.xml.rels")
                            || (n.startsWith("xl/worksheets/sheet") && n.endsWith(".xml"))) {
                        try (InputStream in = zip.getInputStream(entry)) {
                            parts.put(n, readCapped(in));
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("This Excel file couldn't be opened. Save it again as .xlsx and retry.");
        } finally {
            if (tmp != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // temp files are cleaned by the OS
                }
            }
        }
        byte[] sheet = parts.get(firstSheetPath(parts));
        if (sheet == null) {
            throw new IllegalArgumentException("This Excel file has no sheet to read.");
        }
        List<String> shared = parts.containsKey("xl/sharedStrings.xml") ? sharedStrings(parts.get("xl/sharedStrings.xml")) : List.of();
        return sheet(sheet, shared);
    }

    private static byte[] readCapped(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > MAX_PART_BYTES) {
                throw new IllegalArgumentException("This Excel file is too large. Split it into smaller files.");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** The first sheet in the workbook's order, falling back to sheet1.xml. */
    private static String firstSheetPath(Map<String, byte[]> parts) {
        try {
            String rid = null;
            if (parts.containsKey("xl/workbook.xml")) {
                XMLStreamReader r = reader(parts.get("xl/workbook.xml"));
                while (r.hasNext() && rid == null) {
                    if (r.next() == XMLStreamConstants.START_ELEMENT && r.getLocalName().equals("sheet")) {
                        for (int i = 0; i < r.getAttributeCount(); i++) {
                            if (r.getAttributeLocalName(i).equals("id")) {
                                rid = r.getAttributeValue(i);
                            }
                        }
                    }
                }
            }
            if (rid != null && parts.containsKey("xl/_rels/workbook.xml.rels")) {
                XMLStreamReader r = reader(parts.get("xl/_rels/workbook.xml.rels"));
                while (r.hasNext()) {
                    if (r.next() == XMLStreamConstants.START_ELEMENT && r.getLocalName().equals("Relationship")
                            && rid.equals(r.getAttributeValue(null, "Id"))) {
                        String target = r.getAttributeValue(null, "Target").replaceFirst("^/?(xl/)?", "");
                        return "xl/" + target;
                    }
                }
            }
        } catch (XMLStreamException e) {
            // fall through to the usual name
        }
        return "xl/worksheets/sheet1.xml";
    }

    private static List<String> sharedStrings(byte[] xml) {
        List<String> out = new ArrayList<>();
        try {
            XMLStreamReader r = reader(xml);
            StringBuilder current = null;
            boolean inText = false;
            boolean inPhonetic = false;
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "si" -> current = new StringBuilder();
                        case "rPh" -> inPhonetic = true;
                        case "t" -> inText = !inPhonetic;
                        default -> { }
                    }
                } else if (ev == XMLStreamConstants.CHARACTERS || ev == XMLStreamConstants.CDATA) {
                    if (inText && current != null) {
                        current.append(r.getText());
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "si" -> {
                            out.add(current == null ? "" : current.toString());
                            current = null;
                        }
                        case "rPh" -> inPhonetic = false;
                        case "t" -> inText = false;
                        default -> { }
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException("This Excel file couldn't be read. Save it again as .xlsx and retry.");
        }
        return out;
    }

    private static List<List<String>> sheet(byte[] xml, List<String> shared) {
        List<List<String>> rows = new ArrayList<>();
        try {
            XMLStreamReader r = reader(xml);
            List<String> row = null;
            int col = 0;
            String type = null;
            StringBuilder value = null;
            boolean inValue = false;
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "row" -> {
                            if (rows.size() >= MAX_ROWS) {
                                throw new IllegalArgumentException("This file has more than " + MAX_ROWS + " rows. Split it into smaller files.");
                            }
                            row = new ArrayList<>();
                            col = 0;
                        }
                        case "c" -> {
                            String ref = r.getAttributeValue(null, "r");
                            col = ref == null ? col : column(ref);
                            type = r.getAttributeValue(null, "t");
                            value = new StringBuilder();
                        }
                        case "v", "t" -> inValue = value != null;
                        default -> { }
                    }
                } else if ((ev == XMLStreamConstants.CHARACTERS || ev == XMLStreamConstants.CDATA) && inValue) {
                    value.append(r.getText());
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "v", "t" -> inValue = false;
                        case "c" -> {
                            if (row != null && col < MAX_COLUMNS) {
                                while (row.size() < col) {
                                    row.add("");
                                }
                                row.add(cell(type, value == null ? "" : value.toString(), shared));
                            }
                            col++;
                            value = null;
                        }
                        case "row" -> {
                            if (row != null) {
                                rows.add(row);
                            }
                            row = null;
                        }
                        default -> { }
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException("This Excel file couldn't be read. Save it again as .xlsx and retry.");
        }
        return rows;
    }

    private static String cell(String type, String raw, List<String> shared) {
        if ("s".equals(type)) {
            try {
                int i = Integer.parseInt(raw.trim());
                return i >= 0 && i < shared.size() ? shared.get(i).strip() : "";
            } catch (NumberFormatException e) {
                return "";
            }
        }
        if ("b".equals(type)) {
            return "1".equals(raw) ? "TRUE" : "FALSE";
        }
        String v = raw.strip();
        // Numbers such as phone numbers come as 7676211809 or 7.676211809E9.
        if (v.matches("-?\\d+(\\.\\d+)?E\\d+")) {
            try {
                return new java.math.BigDecimal(v).toPlainString();
            } catch (NumberFormatException e) {
                return v;
            }
        }
        return v.endsWith(".0") && v.matches("-?\\d+\\.0") ? v.substring(0, v.length() - 2) : v;
    }

    /** "AB12" → 27 (zero-based column). */
    static int column(String ref) {
        int col = 0;
        for (char ch : ref.toCharArray()) {
            if (ch >= 'A' && ch <= 'Z') {
                col = col * 26 + (ch - 'A' + 1);
            } else if (ch >= 'a' && ch <= 'z') {
                col = col * 26 + (ch - 'a' + 1);
            } else {
                break;
            }
        }
        return Math.max(0, col - 1);
    }

    private static XMLStreamReader reader(byte[] xml) throws XMLStreamException {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return f.createXMLStreamReader(new ByteArrayInputStream(xml));
    }

    // ---- csv ----

    static List<List<String>> csv(String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(cell.toString().strip());
                cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString().strip());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                if (rows.size() > MAX_ROWS) {
                    throw new IllegalArgumentException("This file has more than " + MAX_ROWS + " rows. Split it into smaller files.");
                }
            } else {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString().strip());
            rows.add(row);
        }
        return rows;
    }
}
