package com.codewalnut.ats.bank;

import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Checks question pictures before they are stored (ADR-0014): an SVG without scripts, event
 * handlers or external links, or a PNG/JPEG data URI checked by content, up to 1 MB. Pictures are
 * shown through &lt;img&gt;, where SVG scripts never run; this is defence in depth.
 */
public final class Figures {

    static final int MAX_BYTES = 1024 * 1024;
    private static final Pattern UNSAFE_SVG = Pattern.compile(
            "(?i)<script|<foreignObject|\\son\\w+\\s*=|javascript:|<iframe|<object|<embed|(?:xlink:)?href\\s*=\\s*[\"'](?!#)");

    private Figures() {}

    /** @return the figure, or null when blank; @throws IllegalArgumentException when it isn't allowed */
    public static String check(String figure) {
        if (figure == null || figure.isBlank()) {
            return null;
        }
        String f = figure.strip();
        if (f.startsWith("<svg")) {
            if (f.length() > MAX_BYTES || UNSAFE_SVG.matcher(f).find()) {
                throw new IllegalArgumentException("figure: this drawing can't be used");
            }
            return f;
        }
        for (String type : new String[] {"png", "jpeg"}) {
            String prefix = "data:image/" + type + ";base64,";
            if (f.startsWith(prefix)) {
                byte[] bytes;
                try {
                    bytes = Base64.getDecoder().decode(f.substring(prefix.length()));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("figure: the picture couldn't be read");
                }
                if (bytes.length > MAX_BYTES) {
                    throw new IllegalArgumentException("figure: pictures must be 1 MB or smaller");
                }
                boolean ok = type.equals("png")
                        ? bytes.length > 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                        : bytes.length > 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF;
                if (!ok) {
                    throw new IllegalArgumentException("figure: only PNG or JPG pictures are accepted");
                }
                return f;
            }
        }
        throw new IllegalArgumentException("figure: only PNG or JPG pictures are accepted");
    }
}
