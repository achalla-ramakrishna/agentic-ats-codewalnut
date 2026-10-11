package com.codewalnut.ats.naukri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the rows of a Naukri applicant export into {@link NaukriProfile}s by their column headings
 * (ADR-0026). Headings are matched loosely so small changes in the export still work; "NA" means
 * empty. Columns not about the job (gender, date of birth, home town) are skipped on purpose.
 */
public final class NaukriSheet {

    private static final Pattern YEARS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:year|yr)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONTHS = Pattern.compile("(\\d+)\\s*month", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL = Pattern.compile("^[a-z0-9._%+'-]+@[a-z0-9.-]+\\.[a-z]{2,}$");
    private static final Pattern PLAIN_NUMBER = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*\\+?\\s*$");

    private enum Field {
        NAME("name", "candidate name", "full name"),
        EMAIL("email id", "email", "e-mail", "email address"),
        PHONE("phone number", "mobile", "phone", "contact number", "mobile number"),
        CURRENT_LOCATION("current location", "location"),
        PREFERRED_LOCATIONS("preferred locations", "preferred location"),
        EXPERIENCE("total experience", "experience", "work experience"),
        COMPANY("curr. company name", "current company name", "current company", "company"),
        DESIGNATION("curr. company designation", "current designation", "designation"),
        SALARY("annual salary", "current ctc", "ctc", "salary"),
        NOTICE("notice period/ availability to join", "notice period", "availability to join"),
        HEADLINE("resume headline", "headline", "profile summary", "summary"),
        KEY_SKILLS("key skills", "skills", "it skills"),
        UG_DEGREE("under graduation degree", "ug degree", "graduation"),
        UG_COLLEGE("ug university/institute name", "ug institute", "ug university"),
        PG_DEGREE("pg specialization", "pg degree", "post graduation degree"),
        PG_COLLEGE("pg university/institute name", "pg institute", "pg university"),
        APPLIED_ON("date of application", "applied on", "application date");

        final List<String> names;

        Field(String... names) {
            this.names = List.of(names);
        }
    }

    public record Parsed(List<NaukriProfile> profiles, int headerRow, List<String> missing) {}

    private NaukriSheet() {}

    public static Parsed parse(List<List<String>> rows) {
        int header = -1;
        Map<Field, Integer> columns = Map.of();
        for (int i = 0; i < Math.min(rows.size(), 15) && header < 0; i++) {
            Map<Field, Integer> found = columns(rows.get(i));
            if (found.containsKey(Field.NAME) && (found.containsKey(Field.EMAIL) || found.containsKey(Field.PHONE))) {
                header = i;
                columns = found;
            }
        }
        if (header < 0) {
            throw new IllegalArgumentException(
                    "Couldn't find the heading row. The sheet needs at least Name and Email ID (or Phone Number) columns, as in Naukri's export.");
        }
        List<NaukriProfile> out = new ArrayList<>();
        for (int i = header + 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.stream().allMatch(c -> value(c) == null)) {
                continue;
            }
            Map<Field, Integer> c = columns;
            java.util.function.Function<Field, String> get = f -> c.containsKey(f) && c.get(f) < row.size() ? value(row.get(c.get(f))) : null;
            String experience = get.apply(Field.EXPERIENCE);
            String email = get.apply(Field.EMAIL);
            out.add(new NaukriProfile(i + 1, cap(get.apply(Field.NAME), 200), email(email),
                    phone(get.apply(Field.PHONE)), cap(get.apply(Field.CURRENT_LOCATION), 200), cap(get.apply(Field.PREFERRED_LOCATIONS), 500),
                    years(experience), cap(experience, 100), cap(get.apply(Field.COMPANY), 200), cap(get.apply(Field.DESIGNATION), 200),
                    cap(get.apply(Field.SALARY), 100), cap(get.apply(Field.NOTICE), 100), cap(get.apply(Field.HEADLINE), 2000),
                    cap(get.apply(Field.KEY_SKILLS), 2000), cap(get.apply(Field.UG_DEGREE), 200), cap(get.apply(Field.UG_COLLEGE), 300),
                    cap(get.apply(Field.PG_DEGREE), 200), cap(get.apply(Field.PG_COLLEGE), 300), cap(get.apply(Field.APPLIED_ON), 50)));
        }
        List<String> missing = new ArrayList<>();
        if (!columns.containsKey(Field.HEADLINE) && !columns.containsKey(Field.KEY_SKILLS)) {
            missing.add("Resume Headline or Key Skills");
        }
        if (!columns.containsKey(Field.EXPERIENCE)) {
            missing.add("Total Experience");
        }
        return new Parsed(out, header + 1, missing);
    }

    /** Each heading matched to a field; exact names win over partial ones. */
    private static Map<Field, Integer> columns(List<String> headings) {
        Map<Field, Integer> found = new HashMap<>();
        for (int pass = 0; pass < 2; pass++) {
            for (int col = 0; col < headings.size(); col++) {
                String h = headings.get(col) == null ? "" : headings.get(col).toLowerCase(Locale.ROOT).strip();
                if (h.isEmpty() || found.containsValue(col)) {
                    continue;
                }
                for (Field f : Field.values()) {
                    if (found.containsKey(f)) {
                        continue;
                    }
                    boolean match = pass == 0 ? f.names.contains(h) : f.names.stream().anyMatch(n -> n.length() > 4 && h.contains(n));
                    if (match) {
                        found.put(f, col);
                        break;
                    }
                }
            }
        }
        return found;
    }

    private static String value(String cell) {
        if (cell == null) {
            return null;
        }
        String v = cell.strip();
        return v.isEmpty() || v.equalsIgnoreCase("NA") || v.equalsIgnoreCase("N/A") || v.equals("-") ? null : v;
    }

    private static String cap(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }

    /** "6 Year(s) 5 Month(s)" → 6.4; "4" → 4.0; "Fresher" → 0. */
    public static Double years(String experience) {
        if (experience == null) {
            return null;
        }
        if (experience.toLowerCase(Locale.ROOT).contains("fresher")) {
            return 0.0;
        }
        Matcher plain = PLAIN_NUMBER.matcher(experience);
        if (plain.matches()) {
            return Double.parseDouble(plain.group(1));
        }
        Matcher y = YEARS.matcher(experience);
        Matcher m = MONTHS.matcher(experience);
        if (!y.find() && !m.find()) {
            return null;
        }
        double years = y.find(0) ? Double.parseDouble(y.group(1)) : 0;
        double months = m.find(0) ? Integer.parseInt(m.group(1)) : 0;
        return Math.round((years + months / 12.0) * 10) / 10.0;
    }

    /** The first number in the cell, 10 to 15 digits, keeping a leading +; otherwise null. */
    public static String phone(String raw) {
        if (raw == null) {
            return null;
        }
        String first = raw.split("[,/;]")[0].strip();
        String digits = first.replaceAll("[^0-9]", "");
        if (digits.length() < 10 || digits.length() > 15) {
            return null;
        }
        return (first.startsWith("+") ? "+" : "") + digits;
    }

    /** The first valid address in the cell, lower-cased; otherwise null. */
    public static String email(String raw) {
        if (raw == null) {
            return null;
        }
        // Some applicants give two addresses ("a@x.com, b@y.com"): take the first valid one.
        for (String part : raw.toLowerCase(Locale.ROOT).split("[,;/\\s]+")) {
            if (EMAIL.matcher(part).matches() && part.length() <= 254) {
                return part;
            }
        }
        return null;
    }
}
