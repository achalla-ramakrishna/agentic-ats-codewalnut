package com.codewalnut.ats.service;

/** Phone numbers in the international digits-only form WhatsApp uses (e.g. 919000011111). */
public final class PhoneNumbers {

    private PhoneNumbers() {}

    /** Returns null when the number can't be a mobile number. */
    public static String forWhatsApp(String phone, String defaultCountryCode) {
        if (phone == null) {
            return null;
        }
        boolean international = phone.strip().startsWith("+") || phone.strip().startsWith("00");
        String digits = phone.replaceAll("\\D", "");
        if (phone.strip().startsWith("00")) {
            digits = digits.substring(2);
        }
        if (!international) {
            if (digits.length() == 11 && digits.startsWith("0")) {
                digits = digits.substring(1);
            }
            if (digits.length() == 10) {
                digits = defaultCountryCode + digits;
            }
        }
        return digits.length() >= 11 && digits.length() <= 15 ? digits : null;
    }
}
