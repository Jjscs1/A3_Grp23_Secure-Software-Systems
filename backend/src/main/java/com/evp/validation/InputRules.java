package com.evp.validation;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * SEC-06: single source of truth for allow-list input rules (pure Java, no Spring).
 * Allow-lists are the primary defence in depth; parameterised queries are the primary
 * defence against SQL injection and output encoding (React) against XSS.
 */
public final class InputRules {

    public enum TextKind { NAME, ADDRESS, DIVISION, ORGANISATION, REFERENCE }

    private static final Pattern NAME = Pattern.compile("^[\\p{L}\\p{M}][\\p{L}\\p{M}\\p{Zs}'\u2019.\\-]*$");
    private static final Pattern ADDRESS = Pattern.compile("^[\\p{L}\\p{M}\\p{N}\\p{Zs},.'\u2019\\-/#]+$");
    private static final Pattern ORGANISATION = Pattern.compile("^[\\p{L}\\p{M}\\p{N}\\p{Zs}'\u2019.&\\-]+$");
    private static final Pattern REFERENCE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 ._/\\-]*$");
    private static final Pattern POSTCODE = Pattern.compile("^\\d{4}$");
    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private InputRules() { }

    /** C0 controls (incl. NUL, CR, LF), DEL and C1 controls. */
    public static boolean hasControlChars(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || (c >= 0x7f && c <= 0x9f)) {
                return true;
            }
        }
        return false;
    }

    /** SQL comment tokens: never legitimate in names/addresses (defence in depth only). */
    public static boolean hasSqlCommentToken(String s) {
        return s.contains("--") || s.contains("/*") || s.contains("*/");
    }

    public static boolean check(TextKind kind, String raw, int min, int max) {
        if (raw == null) {
            return false;
        }
        String s = raw.strip();
        if (s.length() < min || s.length() > max) {
            return false;
        }
        if (hasControlChars(raw) || hasSqlCommentToken(s)) {
            return false;
        }
        Pattern p = switch (kind) {
            case NAME, DIVISION -> NAME;
            case ADDRESS -> ADDRESS;
            case ORGANISATION -> ORGANISATION;
            case REFERENCE -> REFERENCE;
        };
        return p.matcher(s).matches();
    }

    public static boolean isPostcode(String s) {
        return s != null && POSTCODE.matcher(s).matches();
    }

    public static boolean isIsoPastDate(String s) {
        return isIsoPastDate(s, LocalDate.now(ZoneOffset.UTC));
    }

    /** Real calendar date YYYY-MM-DD between 1900-01-01 and {@code today}. */
    public static boolean isIsoPastDate(String s, LocalDate today) {
        if (s == null || !ISO_DATE.matcher(s).matches()) {
            return false;
        }
        try {
            LocalDate d = LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE); // strict: rejects 2020-02-31
            return d.getYear() >= 1900 && !d.isAfter(today);
        } catch (DateTimeException e) {
            return false;
        }
    }
}
