package com.hometv.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * First run's rules (design_preview/README.md, First run; src/screens/setup.ts), kept apart from the views so they
 * are tested on the JVM: which steps show, when a row counts as done, where focus goes back to, the footer's step
 * and Next, the keys on the checklist, the Streaming services and Stores rows' names, how the done rows are kept, and
 * whether the checklist opens by itself on this start.
 */
final class FirstRunRules {
    private FirstRunRules() {}

    /**
     * The checklist's rows, in order, as SettingsSection ids. Since F14 (the project lead, 2026-11-01) Features comes
     * first and there is no other on/off: its setups (Sign in to TMDB, Phone notes, Weather city) are tabs under it, not
     * rows of their own. Region (F12) comes before Streaming services and Stores, since it decides which of them show.
     */
    static final List<String> STEPS = Collections.unmodifiableList(Arrays.asList(
            "features", "region", "services", "stores"));

    /** The steps this build shows: STEPS among the sections present. */
    static List<String> steps(Collection<String> present) {
        List<String> out = new ArrayList<>();
        for (String id : STEPS) if (present.contains(id)) out.add(id);
        return out;
    }

    /**
     * The setup a feature turned on in the Features step opens, as a SettingsSection id (F14, design_preview's
     * features.ts): TMDB account signs in, Weather picks the city, Phone notes pairs a phone. Price lookup's keys page is
     * Builder E's F16 and opens nothing until it exists.
     */
    static String setupOf(Features.Feature f) {
        switch (f) {
            case TMDB: return "account";
            case NOTES: return "notes";
            case WEATHER: return "weather";
            default: return null;
        }
    }

    /** Every setup the Features step can open, as SettingsSection ids. */
    static final List<String> SETUPS = Collections.unmodifiableList(Arrays.asList("account", "notes", "weather"));

    /** The setups' tabs under Features (F28): one per feature on that has a setup, in the features' order. */
    static List<String> setups(Features on) {
        List<String> out = new ArrayList<>();
        for (Features.Feature f : Features.Feature.values()) {
            String id = setupOf(f);
            if (id != null && on.on(f)) out.add(id);
        }
        return out;
    }

    /** The feature whose setup that section is, or null. */
    static Features.Feature featureOf(String setupId) {
        for (Features.Feature f : Features.Feature.values()) {
            if (setupId != null && setupId.equals(setupOf(f))) return f;
        }
        return null;
    }

    /**
     * Whether a feature's setup is finished (the project lead, 2026-11-02): when First run ends, a feature on without it
     * is switched off, so nothing shows half set up. TMDB account: the TV is signed in. Phone notes: TMDB account is set
     * up and the TV is listening (Phone notes' own switch; the TV can't tell a phone paired until it sends a note).
     * Price lookup: the Watchmode and Movie of the Night keys are set (F16's keys page, or the build's). Weather: a
     * place is set, which the TV's default always is. A build without a TMDB token ({@code tmdbBuild} false: the
     * emulator, CI) has no sign-in to finish, so TMDB account and Phone notes count as set up there and stay on.
     */
    static boolean setUp(Features.Feature f, boolean tmdbBuild, boolean signedIn, boolean notesListening,
                         boolean priceKeys) {
        switch (f) {
            case TMDB: return !tmdbBuild || signedIn;
            case NOTES: return !tmdbBuild || (signedIn && notesListening);
            case PRICES: return priceKeys;
            default: return true;
        }
    }

    /**
     * The panels in the order Next steps through them (the project lead, 2026-11-02): Features, then each setup under
     * it, then the other steps.
     */
    static List<String> panels(List<String> steps, List<String> setups) {
        List<String> out = new ArrayList<>();
        for (String id : steps) {
            out.add(id);
            if (FeaturesSection.ID.equals(id)) out.addAll(setups);
        }
        return out;
    }

    /** The panel Next opens after id; null on the last, whose button is Done. */
    static String nextPanel(List<String> panels, String id) {
        int i = panels.indexOf(id);
        return i < 0 || i + 1 >= panels.size() ? null : panels.get(i + 1);
    }

    /**
     * Whether the launcher must be rebuilt before the feature's setup can work: TMDB account's sign-in needs the TMDB
     * client the launcher only makes with it on, and Phone notes' pairing its inbox. Not when the launcher already runs
     * with it (switched off and on again since it was built).
     */
    static boolean setupNeedsRebuild(Features.Feature f, boolean runsNow) {
        return !runsNow && (f == Features.Feature.TMDB || f == Features.Feature.NOTES);
    }

    /**
     * When a row counts as done (the project lead, 2026-10-31; BACKLOG.md, First run): as in the prototype, once its
     * pane was opened, whatever was chosen there. Kept across restarts ({@link #encode}).
     */
    static boolean done(String id, Set<String> opened) {
        return opened.contains(id);
    }

    static int doneCount(List<String> steps, Set<String> opened) {
        int n = 0;
        for (String id : steps) if (done(id, opened)) n++;
        return n;
    }

    static boolean allDone(List<String> steps, Set<String> opened) {
        return doneCount(steps, opened) == steps.size();
    }

    /** Where focus goes back to from a step: the first row not done, or null for Start when every row is. */
    static String nextFocus(List<String> steps, Set<String> opened) {
        for (String id : steps) if (!done(id, opened)) return id;
        return null;
    }

    /** The "N" of "Step N of M" for a step (1-based); 0 when it is not one of the steps. */
    static int stepNumber(List<String> steps, String id) {
        return steps.indexOf(id) + 1;
    }

    /** The step the footer's Next opens after id; null on the last step, whose button is Done. */
    static String nextStep(List<String> steps, String id) {
        int i = steps.indexOf(id);
        return i < 0 || i + 1 >= steps.size() ? null : steps.get(i + 1);
    }

    /**
     * Whether the account's value is only "Not signed in": the checklist then shows no value (its pill says
     * Optional), and the split's row says Optional instead (settings.ts paintValues).
     */
    static boolean placeholder(String id, String value, String notSignedIn) {
        return "account".equals(id) && value != null && value.equals(notSignedIn);
    }

    /**
     * Whether a row's value only repeats its pill (Features' "All off" beside the All off pill): the checklist leaves
     * it out while the pill shows.
     */
    static boolean repeatsPill(String value, String pill) {
        return value != null && pill != null && value.trim().equals(pill.trim());
    }

    /**
     * Whether the checklist opens by itself on this start (the project lead, 2026-10-31; BACKLOG.md, First run): once,
     * on the first start after a fresh install, never after an update; config.xml's first_run_checklist (or a pushed
     * config.json) set to false turns it off.
     */
    static boolean showOnStart(boolean configured, boolean freshInstall, boolean shownBefore) {
        return configured && freshInstall && !shownBefore;
    }

    /**
     * Whether the package is still as first installed: Android gives a fresh install the same first-install and
     * last-update times, and moves the last-update time on every update after it.
     */
    static boolean freshInstall(long firstInstallTime, long lastUpdateTime) {
        return firstInstallTime == lastUpdateTime;
    }

    /** The ticked among order, in that order: the pane's order for the row's names. */
    static List<ServiceId> ticked(Collection<ServiceId> order, Set<ServiceId> ticked) {
        List<ServiceId> out = new ArrayList<>();
        for (ServiceId s : order) if (ticked.contains(s)) out.add(s);
        return out;
    }

    /**
     * A row's value from the names ticked on its pane, in the pane's order (setup.ts, names): "Netflix, Prime Video +6";
     * one or two names alone; "" for none, which leaves the value out.
     */
    static String names(List<String> ticked) {
        int max = 2;
        if (ticked.size() <= max) return join(ticked);
        return join(ticked.subList(0, max)) + " +" + (ticked.size() - max);
    }

    private static String join(List<String> names) {
        StringBuilder b = new StringBuilder();
        for (String n : names) {
            if (b.length() > 0) b.append(", ");
            b.append(n);
        }
        return b.toString();
    }

    /** The done steps as First run's preferences keep them: "services,stores" in the steps' order. */
    static String encode(Collection<String> opened) {
        StringBuilder b = new StringBuilder();
        for (String id : STEPS) {
            if (!opened.contains(id)) continue;
            if (b.length() > 0) b.append(',');
            b.append(id);
        }
        return b.toString();
    }

    /** The kept done steps, anything that is not a step left out; none for null. */
    static List<String> decode(String saved) {
        List<String> out = new ArrayList<>();
        if (saved == null) return out;
        for (String part : saved.split(",")) {
            String id = part.trim();
            if (STEPS.contains(id) && !out.contains(id)) out.add(id);
        }
        return out;
    }

    enum Move {
        /** The Shell's own handling: spatial moves, OK. */
        PASS,
        /** Nothing: → on a row (the help pane has nothing to focus), ← and → on Start. */
        STAY,
        /** ↓ from the last row. */
        START,
        /** Back: First run goes Home. */
        HOME
    }

    /** A key on the checklist, with focus on Start or on a row (the last one or not). */
    static Move onKey(Screen.Key key, boolean onStart, boolean onLastRow) {
        if (key == Screen.Key.BACK) return Move.HOME;
        if (onStart) return key == Screen.Key.LEFT || key == Screen.Key.RIGHT ? Move.STAY : Move.PASS;
        if (key == Screen.Key.RIGHT) return Move.STAY;
        if (key == Screen.Key.DOWN && onLastRow) return Move.START;
        return Move.PASS;
    }
}
