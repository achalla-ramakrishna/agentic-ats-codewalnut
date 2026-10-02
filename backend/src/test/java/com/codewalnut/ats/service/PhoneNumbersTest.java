package com.codewalnut.ats.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    void indianNumbersGetTheCountryCode() {
        assertThat(PhoneNumbers.forWhatsApp("9000011111", "91")).isEqualTo("919000011111");
        assertThat(PhoneNumbers.forWhatsApp("090000 11111", "91")).isEqualTo("919000011111");
        assertThat(PhoneNumbers.forWhatsApp("+91 90000-11111", "91")).isEqualTo("919000011111");
    }

    @Test
    void internationalNumbersAreKept() {
        assertThat(PhoneNumbers.forWhatsApp("+44 7700 900123", "91")).isEqualTo("447700900123");
        assertThat(PhoneNumbers.forWhatsApp("0044 7700 900123", "91")).isEqualTo("447700900123");
    }

    @Test
    void junkIsRefused() {
        assertThat(PhoneNumbers.forWhatsApp(null, "91")).isNull();
        assertThat(PhoneNumbers.forWhatsApp("12345", "91")).isNull();
        assertThat(PhoneNumbers.forWhatsApp("+1 23", "91")).isNull();
    }
}
