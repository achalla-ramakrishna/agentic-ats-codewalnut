package com.codewalnut.ats.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns a table pasted from Excel / Google Sheets (tab-separated) or a CSV into candidate rows.
 * Forgiving on purpose: finds the name / email / phone columns from a header row when there is
 * one, otherwise by the look of the values; cleans stray characters; and reports problems per
 * row instead of failing the whole paste.
 */
public final class CandidateTableParser {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private CandidateTableParser() {}

    /** One parsed row. email/phone are null when missing or unusable; issues say why. */
    public record Row(int line, String name, String email, String phone, List<String> issues) {

        public boolean hasName() {
            return name != null && !name.isBlank();
        }
    }

    public static List<Row> parse(String text) {
        List<String[]> lines = new ArrayList<>();
        int[] lineNumbers = new int[text.split("\\R", -1).length];
        int count = 0;
        String[] rawLines = text.split("\\R");
        for (int i = 0; i < rawLines.length; i++) {
            String raw = rawLines[i];
            if (raw.isBlank() || raw.replace("\t", "").replace(",", "").isBlank()) {
                continue;
            }
            lines.add(split(raw));
            lineNumbers[count++] = i + 1;
        }
        if (lines.isEmpty()) {
            return List.of();
        }

        int nameCol = -1;
        int emailCol = -1;
        int phoneCol = -1;
        int start = 0;
        String[] first = lines.get(0);
        for (int c = 0; c < first.length; c++) {
            String h = first[c].trim().toLowerCase(Locale.ROOT);
            if (h.contains("name") && nameCol < 0) {
                nameCol = c;
            } else if ((h.contains("email") || h.contains("e-mail") || h.equals("mail")) && emailCol < 0) {
                emailCol = c;
            } else if ((h.contains("phone") || h.contains("mobile") || h.contains("contact")) && phoneCol < 0) {
                phoneCol = c;
            }
        }
        boolean hasHeader = nameCol >= 0 && (emailCol >= 0 || phoneCol >= 0);
        if (hasHeader) {
            start = 1;
        }

        List<Row> rows = new ArrayList<>();
        for (int i = start; i < lines.size(); i++) {
            String[] cells = lines.get(i);
            rows.add(hasHeader
                    ? fromColumns(lineNumbers[i], cell(cells, nameCol), cell(cells, emailCol), cell(cells, phoneCol))
                    : guess(lineNumbers[i], cells));
        }
        return rows;
    }

    private static Row fromColumns(int line, String name, String email, String phone) {
        List<String> issues = new ArrayList<>();
        String cleanName = cleanName(name);
        if (cleanName.isEmpty()) {
            issues.add("Name is missing");
        }
        String cleanEmail = cleanEmail(email, issues);
        String cleanPhone = cleanPhone(phone, issues);
        return new Row(line, cleanName, cleanEmail, cleanPhone, issues);
    }

    /** No header: the email is whatever looks like one, the phone is the long digit run, the name is the first text cell. */
    private static Row guess(int line, String[] cells) {
        String name = null;
        String email = null;
        String phone = null;
        for (String cell : cells) {
            String v = cell.trim();
            if (v.isEmpty()) {
                continue;
            }
            String emailCandidate = stripStray(v).toLowerCase(Locale.ROOT);
            String digits = v.replaceAll("[^0-9]", "");
            if (email == null && EMAIL.matcher(emailCandidate).matches()) {
                email = v;
            } else if (phone == null && digits.length() >= 10 && v.replaceAll("[0-9+()\\-\\s]", "").isEmpty()) {
                phone = v;
            } else if (name == null && !v.matches("\\d{1,4}")) {
                name = v;
            }
        }
        return fromColumns(line, name, email, phone);
    }

    private static String cleanName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    private static String cleanEmail(String raw, List<String> issues) {
        if (raw == null || raw.isBlank()) {
            issues.add("No email");
            return null;
        }
        String email = stripStray(raw.trim()).toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) {
            issues.add("Email doesn't look valid: \"" + raw.trim() + "\" — left blank");
            return null;
        }
        return email;
    }

    private static String cleanPhone(String raw, List<String> issues) {
        if (raw == null || raw.isBlank()) {
            issues.add("No phone");
            return null;
        }
        String trimmed = raw.trim();
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.length() < 10 || digits.length() > 15) {
            issues.add("Phone doesn't look valid: \"" + trimmed + "\" — left blank");
            return null;
        }
        return (trimmed.startsWith("+") ? "+" : "") + digits;
    }

    /** Drops stray punctuation people paste around emails, e.g. a trailing "|" or quotes. */
    private static String stripStray(String v) {
        return v.replaceAll("^[\\s|;,\"'<>]+|[\\s|;,\"'<>.]+$", "");
    }

    private static String cell(String[] cells, int col) {
        return col >= 0 && col < cells.length ? cells[col] : null;
    }

    private static String[] split(String line) {
        if (line.contains("\t")) {
            return line.split("\t", -1);
        }
        return line.split(",", -1);
    }
}
