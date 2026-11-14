package com.codewalnut.ats.service;

/** Test access to ContactExportService helpers. */
public final class ContactExportServiceAccess {
    private ContactExportServiceAccess() {}

    public static String cell(String v) {
        return ContactExportService.cell(v);
    }

    public static java.util.List<String> phones(String v) {
        return ContactExportService.phones(v);
    }
}
