package com.hometv.launcher;

/**
 * F26: Back from an app the launcher opened (a trailer in YouTube, a title in a service's app) returns to where the
 * household was, with its state: the page, Search's query, a library page's filters and the title preview. While the
 * launcher's process lives that is simply the activity as it was. On a 1 GB TV Android often ends the process while the
 * other app plays; the activity is then made again with the Bundle it saved (MainActivity's place), and this says
 * whether that place is still worth going back to.
 *
 * <p>The same rules as a library page's choices (LibraryRecall, F2): kept for an hour away, and not across standby. A
 * process ended in standby can't hear the screen go off, so standby is read from the clocks: the uptime clock stops
 * while the TV sleeps and the elapsed one doesn't, and a gap of more than a minute between them means the TV slept.
 * A TV that stays awake in standby shows no gap, and only the hour applies there. Pure Java, unit-tested.
 */
final class Comeback {

    /** How long away still goes back to the place: an hour, as a library page keeps its choices. */
    static final long AWAY_MS = LibraryRecall.AWAY_MS;
    /** More sleep than this since the place was saved means the TV was in standby. */
    static final long SLEPT_MS = 60 * 1000L;
    /** How far the sleep reading may wobble between two reads of the clocks. */
    static final long JITTER_MS = 1000L;

    private Comeback() {
    }

    /**
     * Whether a place kept on disk (SavedPlace) comes from this run: the same boot ({@code Settings.Global.BOOT_COUNT},
     * -1 where the TV doesn't keep one), the same install of the launcher (its APK's path, new with each install) and the
     * same Home task. Android keeps the task when it only ends the process; a force stop, a crash, a restart or an
     * install ends it, and Home then starts as usual, as a Home press asks.
     */
    static boolean sameRun(int savedBoot, int nowBoot, String savedApk, String nowApk, int savedTask, int nowTask) {
        return savedBoot == nowBoot && savedApk != null && savedApk.equals(nowApk) && savedTask == nowTask;
    }

    /**
     * Whether a place saved at {@code savedAt} (elapsed realtime), when the TV had slept {@code savedSlept} ms in all
     * (elapsed minus uptime), is still fresh at {@code now}, with {@code nowSlept}. A clock that went back means the TV
     * restarted.
     */
    static boolean fresh(long savedAt, long savedSlept, long now, long nowSlept) {
        long away = now - savedAt;
        if (away < 0 || away > AWAY_MS) return false;
        // The two clocks are read one after the other and rounded on their own, so the gap can read a millisecond
        // lower than before; a restart is caught by the elapsed clock going back.
        long slept = nowSlept - savedSlept;
        return slept >= -JITTER_MS && slept <= SLEPT_MS;
    }
}
