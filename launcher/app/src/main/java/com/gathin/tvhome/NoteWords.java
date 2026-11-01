package com.gathin.tvhome;

import java.util.Locale;

/**
 * Phone notes' words that need no Android (BACKLOG.md, Phone notes; design_preview/README.md, Phone notes and Import
 * pill): what the import pill says, and Launcher settings' value for the section. The sentences themselves are string
 * resources handed in as {@link Texts}, so the tests format the same English the TV shows. Pure Java, unit-tested
 * (NoteWordsTest).
 */
final class NoteWords {
    private NoteWords() {}

    /** The pill's states: importing, then for a few seconds how it ended. */
    enum Kind {
        IMPORTING(false), ADDED(false), UNMATCHED(true), OFFLINE(true), FAILED(true), SIGNED_OUT(true);

        /** Shown in amber: something for the household to know about. */
        final boolean warn;

        Kind(boolean warn) {
            this.warn = warn;
        }
    }

    /** What the pill shows. */
    static final class Pill {
        final Kind kind;
        final String list;
        /** Importing: new lines looked up so far, of how many (0 while the list is found). */
        final int done, total;
        /** At the end: titles added and lines that matched nothing. */
        final int added, unmatched;

        private Pill(Kind kind, String list, int done, int total, int added, int unmatched) {
            this.kind = kind;
            this.list = list;
            this.done = done;
            this.total = total;
            this.added = added;
            this.unmatched = unmatched;
        }

        static Pill importing(String list, int done, int total) {
            return new Pill(Kind.IMPORTING, list, done, total, 0, 0);
        }

        /** How an import ended, from its outcome. */
        static Pill ended(String list, NoteImport.Status status, int added, int unmatched) {
            switch (status) {
                case DONE:
                    return new Pill(unmatched > 0 ? Kind.UNMATCHED : Kind.ADDED, list, 0, 0, added, unmatched);
                case OFFLINE:
                    return new Pill(Kind.OFFLINE, list, 0, 0, added, unmatched);
                case SIGNED_OUT:
                    return new Pill(Kind.SIGNED_OUT, list, 0, 0, added, unmatched);
                default:
                    return new Pill(Kind.FAILED, list, 0, 0, added, unmatched);
            }
        }

        /** The thin bar's fill, 0 to 1. */
        float fraction() {
            return total <= 0 ? 0f : Math.max(0f, Math.min(1f, done / (float) total));
        }
    }

    /** The format strings, from strings_notes.xml. */
    static final class Texts {
        /** "Importing %1$s", "Importing %1$s · %2$d of %3$d". */
        final String importing, importingCount;
        /** "%1$s · %2$d added", "%1$s · %2$d added · %3$d not matched". */
        final String added, addedUnmatched;
        /** "%1$s · waiting for the internet", "%1$s · couldn't reach TMDB", "%1$s · waiting for TMDB sign-in". */
        final String offline, failed, signedOut;
        /** The section's value: "On", "Off", "%1$d not matched". */
        final String on, off, notMatched;

        Texts(String importing, String importingCount, String added, String addedUnmatched, String offline,
              String failed, String signedOut, String on, String off, String notMatched) {
            this.importing = importing;
            this.importingCount = importingCount;
            this.added = added;
            this.addedUnmatched = addedUnmatched;
            this.offline = offline;
            this.failed = failed;
            this.signedOut = signedOut;
            this.on = on;
            this.off = off;
            this.notMatched = notMatched;
        }
    }

    /** "Importing Rebecca · 6 of 12", "Partner · 8 added", "Rebecca · 9 added · 3 not matched", … */
    static String pill(Pill p, Texts t) {
        switch (p.kind) {
            case IMPORTING:
                return p.total > 0 ? String.format(Locale.getDefault(), t.importingCount, p.list, p.done, p.total)
                        : String.format(Locale.getDefault(), t.importing, p.list);
            case ADDED:
                return String.format(Locale.getDefault(), t.added, p.list, p.added);
            case UNMATCHED:
                return String.format(Locale.getDefault(), t.addedUnmatched, p.list, p.added, p.unmatched);
            case OFFLINE:
                return String.format(Locale.getDefault(), t.offline, p.list);
            case SIGNED_OUT:
                return String.format(Locale.getDefault(), t.signedOut, p.list);
            default:
                return String.format(Locale.getDefault(), t.failed, p.list);
        }
    }

    /** The section's value: Off; On; or, on with lines that matched nothing in the last sends, "3 not matched". */
    static String value(boolean on, int unmatched, Texts t) {
        if (!on) return t.off;
        return unmatched > 0 ? String.format(Locale.getDefault(), t.notMatched, unmatched) : t.on;
    }

    /** Whether the section's value shows in amber. */
    static boolean valueWarns(boolean on, int unmatched) {
        return on && unmatched > 0;
    }

    /** "http://192.168.1.50:8765/notes" for the TV's address, or null without one. */
    static String address(String ipv4, int port) {
        if (ipv4 == null || ipv4.isEmpty()) return null;
        return "http://" + ipv4 + ":" + port + "/notes";
    }

    /**
     * What the pane's QR holds: the setup page, with the pairing code after the # so a phone keeps it to itself
     * ("http://192.168.1.50:8765/setup#K7QM4PXT"); null without an address or a code.
     */
    static String setupUrl(String ipv4, int port, String code) {
        String c = NoteInbox.normalize(code);
        if (ipv4 == null || ipv4.isEmpty() || c.isEmpty()) return null;
        return "http://" + ipv4 + ":" + port + "/setup#" + c;
    }

    /** Where an iCloud shortcut link is allowed to point; anything else is dropped. */
    static final String ICLOUD_SHORTCUTS = "https://www.icloud.com/shortcuts/";

    /**
     * note_shortcut_url as the setup page may link it: an iCloud shortcut link ("https://www.icloud.com/shortcuts/
     * 0a1b…") with nothing that could leave an HTML attribute, else "".
     */
    static String shortcutLink(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (!s.startsWith(ICLOUD_SHORTCUTS) || s.length() == ICLOUD_SHORTCUTS.length() || s.length() > 200) return "";
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '/' || c == ':' || c == '.' || c == '-' || c == '_';
            if (!ok) return "";
        }
        return s;
    }

    /** The setup page with the household's shortcut link (or none) in place of its {{shortcut}} mark. */
    static String setupPage(String template, String rawLink) {
        return template == null ? null : template.replace("{{shortcut}}", shortcutLink(rawLink));
    }
}
