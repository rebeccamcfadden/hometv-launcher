package com.gathin.tvhome;

import android.content.res.Resources;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * A festive skin set by a theme's config.json: {@code festival}, {@code festival_until}, {@code greeting}
 * and {@code accent_color}. While it is active the pills get festive icons (a diya for the network, a bell
 * for Inputs, a marigold for Settings), the greeting is replaced and the focus glow takes the accent colour.
 * It switches itself off after {@code festival_until}, so nobody has to remember to undo it.
 */
final class Festival {

    static final Festival NONE = new Festival("", null, "", 0);

    /** Warm diya gold, the accent when a theme names a festival but no colour. */
    static final int DEFAULT_ACCENT = 0xFFFFB23C;

    /** Lower-case festival name, or "" for none. */
    final String name;
    /** Last day the skin shows, inclusive; null for no end date. */
    final LocalDate until;
    /** Text that replaces the time-of-day greeting; "" uses the festival's own default. */
    final String greeting;
    /** Focus glow colour as 0xAARRGGBB, or 0 for {@link #DEFAULT_ACCENT}. */
    final int accent;

    private Festival(String name, LocalDate until, String greeting, int accent) {
        this.name = name;
        this.until = until;
        this.greeting = greeting;
        this.accent = accent;
    }

    /**
     * Builds the skin from raw config values. Anything malformed turns the skin off rather than leaving a
     * festival on the screen forever: an unreadable end date or an empty name gives {@link #NONE}.
     */
    static Festival parse(String name, String until, String greeting, String accent) {
        String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (n.isEmpty() || n.length() > 40) return NONE;
        LocalDate end = null;
        if (until != null && !until.trim().isEmpty()) {
            try {
                end = LocalDate.parse(until.trim());
            } catch (DateTimeParseException e) {
                return NONE;
            }
        }
        String g = greeting == null ? "" : cleanLabel(greeting, "");
        return new Festival(n, end, g, parseColor(accent));
    }

    /** Whether the skin shows today. */
    boolean active(LocalDate today) {
        return !name.isEmpty() && (until == null || !today.isAfter(until));
    }

    /** The greeting to show while active, or "" to keep the normal time-of-day greeting. */
    String greetingText(Resources res) {
        if (!greeting.isEmpty()) return greeting;
        switch (name) {
            case "dussehra":
                return res.getString(R.string.festival_dussehra_greeting);
            case "diwali":
                return res.getString(R.string.festival_diwali_greeting);
            default:
                return "";
        }
    }

    int accentColor() {
        return accent != 0 ? accent : DEFAULT_ACCENT;
    }

    /** {@code #RRGGBB} to an opaque colour, anything else to 0. */
    static int parseColor(String s) {
        if (s == null) return 0;
        String t = s.trim();
        if (!t.matches("#[0-9A-Fa-f]{6}")) return 0;
        return 0xFF000000 | Integer.parseInt(t.substring(1), 16);
    }

    /**
     * An app's own label as a theme's greeting shows it: control, format (bidi overrides, zero width joiners),
     * line and paragraph separator characters become spaces, runs of spaces collapse, at most 40
     * characters (never cutting a surrogate pair), the fallback (package name) when nothing is left.
     */
    static String cleanLabel(String s, String fallback) {
        if (s == null) return fallback;
        StringBuilder b = new StringBuilder(Math.min(s.length(), 48));
        boolean space = true;   // leading spaces are dropped too
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int type = Character.getType(c);
            boolean blank = c == ' ' || Character.isISOControl(c) || Character.isWhitespace(c) || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR
                    || type == Character.SPACE_SEPARATOR;
            if (blank) {
                if (!space) b.append(' ');
                space = true;
                continue;
            }
            boolean pair = Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1));
            if (b.length() + (pair ? 2 : 1) > 40) break;
            b.append(c);
            if (pair) b.append(s.charAt(++i));
            space = false;
        }
        String out = b.toString().trim();
        return out.isEmpty() ? fallback : out;
    }
}
