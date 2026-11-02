package com.hometv.launcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Whether each app can be opened, asked of PackageManager once and then remembered until the package receiver says
 * the app changed. Home checks every app on its row each time it shows again; without this that was two binder calls
 * per app in front of the first frame back. Main thread only.
 */
final class Launchable {
    private final Predicate<String> ask;
    private final Map<String, Boolean> known = new HashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();

    Launchable(Predicate<String> ask) {
        this.ask = ask;
    }

    boolean test(String pkg) {
        Boolean is = known.get(pkg);
        if (is == null) {
            is = ask.test(pkg);
            known.put(pkg, is);
        }
        return is;
    }

    /** The app was installed, updated, enabled, disabled or removed: ask again next time, and tell who listens. */
    void forget(String pkg) {
        known.remove(pkg);
        changed();
    }

    /** Everything may have changed (apps on removable storage came or went, or no receiver was listening). */
    void forgetAll() {
        known.clear();
        changed();
    }

    void listen(Runnable r) {
        if (!listeners.contains(r)) listeners.add(r);
    }

    void unlisten(Runnable r) {
        listeners.remove(r);
    }

    private void changed() {
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }
}
