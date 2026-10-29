package com.gathin.tvhome;

/**
 * The words and checks around Home's clock (design_preview/src/screens/home.ts, tick), kept free of Android so they
 * are tested on the JVM.
 */
final class HomeClock {
    private HomeClock() {}

    enum Greeting { MORNING, AFTERNOON, EVENING, NIGHT }

    /** 1 January 2025, 00:00 UTC. Older TVs start at the epoch until the network sets the clock. */
    static final long PLAUSIBLE_AFTER_MS = 1_735_689_600_000L;

    /**
     * The prototype's morning before noon and afternoon until six, with the evening ending at ten and the small hours
     * as night, so a late film doesn't start with "Good morning".
     */
    static Greeting greeting(int hour) {
        if (hour >= 5 && hour < 12) return Greeting.MORNING;
        if (hour >= 12 && hour < 18) return Greeting.AFTERNOON;
        if (hour >= 18 && hour < 22) return Greeting.EVENING;
        return Greeting.NIGHT;
    }

    /** Whether the system clock has been set: until then Home hides the clock, the date and the greeting. */
    static boolean plausible(long utcMillis) {
        return utcMillis >= PLAUSIBLE_AFTER_MS;
    }
}
