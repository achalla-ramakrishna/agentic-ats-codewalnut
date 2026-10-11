package com.codewalnut.ats.naukri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds a minimal .xlsx (shared strings, one sheet) the way Naukri's export is laid out. Test data only. */
public final class FakeXlsx {

    private FakeXlsx() {}

    public static byte[] of(List<List<String>> rows) {
        List<String> shared = new ArrayList<>();
        StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            sheet.append("<row r=\"").append(r + 1).append("\">");
            for (int c = 0; c < rows.get(r).size(); c++) {
                String v = rows.get(r).get(c);
                String ref = (char) ('A' + c % 26) + "" + (r + 1);
                if (c >= 26) {
                    ref = "A" + (char) ('A' + c - 26) + (r + 1);
                }
                if (v.matches("\\d{10}")) {
                    sheet.append("<c r=\"").append(ref).append("\"><v>").append(v).append("</v></c>");
                } else {
                    shared.add(v);
                    sheet.append("<c r=\"").append(ref).append("\" t=\"s\"><v>").append(shared.size() - 1).append("</v></c>");
                }
            }
            sheet.append("</row>");
        }
        sheet.append("</sheetData></worksheet>");
        StringBuilder sst = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        for (String s : shared) {
            sst.append("<si><t>").append(s.replace("&", "&amp;").replace("<", "&lt;")).append("</t></si>");
        }
        sst.append("</sst>");
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "xl/worksheets/sheet1.xml", sheet.toString());
            put(zip, "xl/sharedStrings.xml", sst.toString());
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    public static final List<String> NAUKRI_HEADINGS = List.of("Job Title", "Date of application", "Name", "Email ID", "Phone Number",
            "Current Location", "Preferred Locations", "Total Experience", "Curr. Company name", "Curr. Company Designation",
            "Annual Salary", "Notice period/ Availability to join", "Resume Headline", "Under Graduation degree",
            "UG University/institute Name", "PG specialization", "PG university/institute name", "Doctorate specialization",
            "Doctorate university/institute name", "Gender", "Home Town/City", "Date of Birth", "Latest Pipeline Stage",
            "Comment 1", "Candidate profile");

    /** A row in Naukri's column order; gender and date of birth are filled so tests can check they're ignored. */
    public static List<String> row(String name, String email, String phone, String experience, String designation, String headline,
            String location, String notice) {
        return List.of("Senior Java Developer", "09-Oct-2026", name, email, phone, location, location, experience, "Example Tech",
                designation, "Rs 12 Lakhs", notice, headline, "B.Tech / B.E.", "Example Institute of Technology", "NA", "NA", "NA",
                "NA", "Female", "NA", "01-Jan-1995", "NA", "NA", "View profile");
    }
}
