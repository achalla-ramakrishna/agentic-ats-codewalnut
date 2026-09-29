package com.codewalnut.ats.service;

import java.security.SecureRandom;

/** Unguessable ids for shareable job links. */
final class PublicLinks {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private PublicLinks() {}

    static String newSlug() {
        char[] out = new char[12];
        for (int i = 0; i < out.length; i++) {
            out[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(out);
    }
}
