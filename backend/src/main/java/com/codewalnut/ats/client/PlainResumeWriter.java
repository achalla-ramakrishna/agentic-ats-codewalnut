package com.codewalnut.ats.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An offline stand-in for AI résumé writing (dev, demo, tests): copies the résumé text into the
 * CodeWalnut layout section by section, using ALL-CAPS lines as headings. No rewording.
 */
public class PlainResumeWriter implements ResumeWriter {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final Pattern GITHUB = Pattern.compile("(?i)github\\.com/[A-Za-z0-9-]+");

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String model() {
        return "offline-copy";
    }

    @Override
    public BrandedResume write(ResumeAnalyzer.Job job, ResumeAnalyzer.ResumeFile file) {
        String text;
        if (ResumeText.isPdf(file)) {
            text = ResumeText.pdfBestEffort(file.data());
        } else if (ResumeText.isDocx(file)) {
            text = ResumeText.docx(file.data());
        } else {
            throw new CalendarException("Old Word (.doc) files can't be read. Please save it as PDF or .docx and upload again.");
        }
        List<String> lines = Arrays.stream(text.split("\\n")).map(String::strip).filter(l -> !l.isEmpty()).toList();
        if (lines.isEmpty()) {
            throw new CalendarException("No text could be read from this file.");
        }
        Matcher email = EMAIL.matcher(text);
        String name = lines.get(0);
        String headline = "";
        String summary = "";
        List<BrandedResume.Section> sections = new ArrayList<>();
        String title = null;
        List<String> body = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (isHeading(line)) {
                flush(title, body, sections);
                title = capitalise(line);
                body = new ArrayList<>();
            } else if (title == null) {
                if (headline.isEmpty() && !line.contains("@")) {
                    headline = line;
                }
            } else {
                body.add(line);
            }
        }
        flush(title, body, sections);
        List<BrandedResume.Section> rest = new ArrayList<>();
        for (BrandedResume.Section s : sections) {
            if (s.title().equalsIgnoreCase("Summary") || s.title().equalsIgnoreCase("Profile")) {
                summary = String.join(" ", s.entries().get(0).bullets());
            } else {
                rest.add(s);
            }
        }
        Matcher github = GITHUB.matcher(text);
        return new BrandedResume(name, headline, "", email.find() ? email.group() : "", github.find() ? github.group() : "", summary,
                List.of(), rest);
    }

    private static boolean isHeading(String line) {
        return line.length() >= 4 && line.length() <= 40 && line.equals(line.toUpperCase(Locale.ROOT))
                && line.chars().anyMatch(Character::isLetter) && !line.contains("@");
    }

    private static void flush(String title, List<String> body, List<BrandedResume.Section> sections) {
        if (title != null && !body.isEmpty()) {
            sections.add(new BrandedResume.Section(title, List.of(new BrandedResume.Entry("", "", "", List.copyOf(body)))));
        }
    }

    private static String capitalise(String heading) {
        String lower = heading.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
