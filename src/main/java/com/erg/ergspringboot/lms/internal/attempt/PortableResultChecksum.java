package com.erg.ergspringboot.lms.internal.attempt;

import com.erg.ergspringboot.exception.BadRequestException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Re-computes the {@code .ergresult} checksum exactly as the client does: SHA-256 over the canonical JSON of
 * {@code body} (keys sorted by UTF-16 code unit, JS {@code JSON.stringify} string and number formatting).
 * It detects corruption and casual edits, not a determined forger; grading never trusts the file's score.
 */
final class PortableResultChecksum {
    private PortableResultChecksum() { }

    static void verify(Map<String, Object> body, String checksum) {
        if (!hash(body).equalsIgnoreCase(checksum == null ? "" : checksum.trim())) {
            throw new BadRequestException("result file checksum mismatch");
        }
    }

    static String hash(Object body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical(body).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder("sha256:");
            for (byte b : digest) out.append(String.format("%02x", b));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String canonical(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    private static void write(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            quote(out, text);
        } else if (value instanceof Boolean flag) {
            out.append(flag);
        } else if (value instanceof Number number) {
            out.append(number(number));
        } else if (value instanceof Map<?, ?> map) {
            List<String> keys = new ArrayList<>();
            map.keySet().forEach(key -> keys.add(String.valueOf(key)));
            keys.sort(String::compareTo);
            out.append('{');
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) out.append(',');
                quote(out, keys.get(i));
                out.append(':');
                write(out, map.get(keys.get(i)));
            }
            out.append('}');
        } else if (value instanceof Collection<?> items) {
            out.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) out.append(',');
                first = false;
                write(out, item);
            }
            out.append(']');
        } else {
            throw new BadRequestException("result file contains a non-JSON value");
        }
    }

    /** shortcut: plain-decimal numbers only (1e-6 <= |n| < 1e21 like JS); other magnitudes fail the checksum. */
    private static String number(Number number) {
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (!Double.isFinite(d)) throw new BadRequestException("result file contains a non-finite number");
            if (d == 0) return "0";
            return new BigDecimal(Double.toString(d)).stripTrailingZeros().toPlainString();
        }
        return number.toString();
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    boolean lone = Character.isSurrogate(c) && !(Character.isHighSurrogate(c)
                            && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1)))
                            && !(Character.isLowSurrogate(c) && i > 0 && Character.isHighSurrogate(text.charAt(i - 1)));
                    if (c < 0x20 || lone) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }
}
