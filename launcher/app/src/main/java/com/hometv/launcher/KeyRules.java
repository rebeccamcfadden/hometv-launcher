package com.hometv.launcher;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * API keys' rules (F16; design_preview/README.md, API keys; src/core/keys.ts), kept apart from Android so they are
 * tested on the JVM (KeyRulesTest): the three keys, what a key may look like, the state a key shows, which key the TV
 * uses, the row's value, and the one free request that checks a key with its service.
 *
 * <p>A key entered on the phone or laptop page replaces the build's own (tv.properties). Removing a key leaves no key,
 * the build's included (the project lead, 2026-11-02). A refused key is kept to show which one was refused, but never
 * used.
 */
final class KeyRules {
    private KeyRules() {}

    enum Id {
        TMDB("tmdb", "TMDB"),
        WATCHMODE("watchmode", "Watchmode"),
        /** Optional: it only fills in prices where Watchmode has none. */
        MOTN("motn", "Movie of the Night");

        /** The page's and the preferences' name. */
        final String id;
        /** The service's name, as the pages show it. */
        final String service;

        Id(String id, String service) {
            this.id = id;
            this.service = service;
        }

        static Id of(String id) {
            for (Id k : values()) if (k.id.equals(id)) return k;
            return null;
        }

        boolean optional() {
            return this == MOTN;
        }
    }

    enum State {
        /** No key. */
        UNSET("unset"),
        /** Being checked with its service now (never saved). */
        CHECKING("checking"),
        /** Its service answered. */
        OK("ok"),
        /** Its service refused it: kept to show, never used. */
        BAD("bad"),
        /**
         * Never checked: the TV couldn't reach its service when the key came in. Kept, used and checked again later,
         * but not counted as working until a check passes (a key that once worked stays {@link #OK}).
         */
        UNREACHABLE("unreachable"),
        /** The build's own key (tv.properties), not checked; a key entered on the page replaces it. */
        BUILT("built");

        /** The page's and the preferences' name. */
        final String wire;

        State(String wire) {
            this.wire = wire;
        }

        static State of(String wire) {
            for (State s : values()) if (s.wire.equals(wire)) return s;
            return null;
        }

        /** The key is known to work: First run's setups count it, and the row's value counts it as working. */
        boolean works() {
            return this == OK || this == BUILT;
        }

        /** The TV has a key for that service, checked or not yet (refused keys aren't kept as one). */
        boolean set() {
            return works() || this == UNREACHABLE;
        }
    }

    /** What a check leaves: its answer, except that a key that once worked stays working when it can't be reached. */
    static State afterCheck(State before, State answer) {
        return before == State.OK && answer == State.UNREACHABLE ? State.OK : answer;
    }

    /** Longer than any of the three services' keys (a TMDB read access token is about 250 characters). */
    static final int MAX_KEY = 2048;

    /** What tv.properties takes for a TMDB token (app/build.gradle): a JWT's characters. */
    private static final Pattern TMDB = Pattern.compile("[A-Za-z0-9._-]+");
    /** And for a price key: printable ASCII but a space, a double quote or a backslash. */
    private static final Pattern PRICE = Pattern.compile("[!-~&&[^\"\\\\]]+");

    /** Whether a key could be one: the same characters the build takes in tv.properties, at most {@link #MAX_KEY}. */
    static boolean valid(Id id, String key) {
        if (key == null) return false;
        String k = key.trim();
        if (k.isEmpty() || k.length() > MAX_KEY) return false;
        return (id == Id.TMDB ? TMDB : PRICE).matcher(k).matches();
    }

    /** The key's last four characters, the only part the TV and the page show again; "" for none. */
    static String last4(String key) {
        if (key == null) return "";
        String k = key.trim();
        return k.length() <= 4 ? k : k.substring(k.length() - 4);
    }

    /**
     * The state a key shows. {@code saved} is the key entered on the page ("" for none) with the state its check
     * left; {@code removed} that the household removed the key, which leaves the build's key unused too.
     */
    static State state(String saved, State savedState, boolean removed, String built) {
        if (!isBlank(saved)) {
            // A check that never finished (the launcher stopped) counts as not yet reached.
            return savedState == null || savedState == State.UNSET || savedState == State.CHECKING || savedState == State.BUILT
                    ? State.UNREACHABLE : savedState;
        }
        return !removed && !isBlank(built) ? State.BUILT : State.UNSET;
    }

    /** The key the TV uses: the one entered unless it was refused, else the build's unless removed; "" for none. */
    static String effective(String saved, State savedState, boolean removed, String built) {
        if (!isBlank(saved)) return state(saved, savedState, removed, built) == State.BAD ? "" : saved.trim();
        return removed || built == null ? "" : built.trim();
    }

    // ---- The row's value -----------------------------------------------------------------------------------------

    enum Kind {
        /** "1 refused" (amber). */
        REFUSED,
        /** "Checking…". */
        CHECKING,
        /** "Not set": none of the keys needed is set. */
        NOT_SET,
        /** "2 of 3 set". */
        SOME,
        /** "1 not checked" (amber): kept, but the TV couldn't reach its service yet. */
        UNCHECKED,
        /** "3 working". */
        WORKING
    }

    static final class Summary {
        final Kind kind;
        /** Refused, set or working, by kind. */
        final int count;
        final int total;

        Summary(Kind kind, int count, int total) {
            this.kind = kind;
            this.count = count;
            this.total = total;
        }
    }

    /**
     * The section's value over {@code ids} (keys.ts value): refused first, then checking, then what is missing, then
     * keys kept but never checked, then the working count.
     */
    static Summary summary(Collection<Id> ids, Map<Id, State> states) {
        int refused = 0, working = 0, set = 0, unchecked = 0, needed = 0, missing = 0;
        boolean checking = false;
        for (Id id : ids) {
            State s = states.get(id);
            if (s == null) s = State.UNSET;
            if (s == State.BAD) refused++;
            if (s == State.CHECKING) checking = true;
            if (s.works()) working++;
            if (s.set()) set++;
            if (s == State.UNREACHABLE) unchecked++;
            if (!id.optional()) {
                needed++;
                if (s == State.UNSET) missing++;
            }
        }
        if (refused > 0) return new Summary(Kind.REFUSED, refused, ids.size());
        if (checking) return new Summary(Kind.CHECKING, 0, ids.size());
        if (missing > 0) return missing == needed ? new Summary(Kind.NOT_SET, 0, ids.size())
                : new Summary(Kind.SOME, set, ids.size());
        if (unchecked > 0) return new Summary(Kind.UNCHECKED, unchecked, ids.size());
        return new Summary(Kind.WORKING, working, ids.size());
    }

    // ---- The check -----------------------------------------------------------------------------------------------

    /** One GET that costs nothing on the service's free plan and answers 401 for a key it doesn't know. */
    static final class Check {
        final String url;
        final Map<String, String> headers;

        Check(String url, Map<String, String> headers) {
            this.url = url;
            this.headers = headers;
        }
    }

    /**
     * The check for a key (tried live 2026-11-02, with good and bad keys): TMDB's authentication test, Watchmode's
     * region list (0 credits) and Movie of the Night's US country, each with the key in a header, never the address.
     */
    static Check check(Id id, String key) {
        String k = key.trim();
        Map<String, String> h = new LinkedHashMap<>();
        switch (id) {
            case TMDB:
                h.put("Authorization", "Bearer " + k);
                h.put("Accept", "application/json");
                return new Check(TmdbApi.BASE + "/authentication", Collections.unmodifiableMap(h));
            case WATCHMODE:
                h.put("X-API-Key", k);
                return new Check("https://api.watchmode.com/v1/regions/", Collections.unmodifiableMap(h));
            default:
                h.put("X-API-Key", k);
                return new Check("https://api.movieofthenight.com/v4/countries/us", Collections.unmodifiableMap(h));
        }
    }

    /** What a check's answer says: 2xx works, 401 or 403 refused; anything else (a limit, an outage) not reached. */
    static State fromStatus(int status) {
        if (status >= 200 && status < 300) return State.OK;
        if (status == 401 || status == 403) return State.BAD;
        return State.UNREACHABLE;
    }

    // ---- The page's address --------------------------------------------------------------------------------------

    /** What a laptop opens ("http://192.168.1.50:8766/keys"), or null without an address. */
    static String address(String ipv4, int port) {
        if (ipv4 == null || ipv4.isEmpty()) return null;
        return "http://" + ipv4 + ":" + port + "/keys";
    }

    /**
     * What the QR holds: the page, with the code after the # so a phone keeps it to itself
     * ("http://192.168.1.50:8766/keys#K7QM4PXT"); null without an address or a code.
     */
    static String pageUrl(String ipv4, int port, String code) {
        String c = NoteInbox.normalize(code);
        String a = address(ipv4, port);
        return a == null || c.isEmpty() ? null : a + "#" + c;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
