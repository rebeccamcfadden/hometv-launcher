package com.hometv.launcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Whether each app can be opened, and its name, asked of PackageManager once and then remembered until the package
 * receiver says the app changed. Home checks every app on its row each time it shows again, and names each tile it
 * builds; without this that was binder calls per app in front of the first frame back. Main thread only.
 */
final class Launchable {
    private final Predicate<String> ask;
    private final Function<String, String> name;
    private final Map<String, Boolean> known = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();
    private final List<Consumer<String>> listeners = new ArrayList<>();

    Launchable(Predicate<String> ask, Function<String, String> name) {
        this.ask = ask;
        this.name = name;
    }

    boolean test(String pkg) {
        Boolean is = known.get(pkg);
        if (is == null) {
            is = ask.test(pkg);
            known.put(pkg, is);
        }
        return is;
    }

    String label(String pkg) {
        String l = labels.get(pkg);
        if (l == null) {
            l = name.apply(pkg);
            labels.put(pkg, l);
        }
        return l;
    }

    /** The app was installed, updated, enabled, disabled or removed: ask again next time, and tell who listens. */
    void forget(String pkg) {
        known.remove(pkg);
        labels.remove(pkg);
        changed(pkg);
    }

    /** Everything may have changed (apps on removable storage came or went, or no receiver was listening). */
    void forgetAll() {
        known.clear();
        labels.clear();
        changed(null);
    }

    /** Listeners hear the package that changed, or null when any may have. */
    void listen(Consumer<String> l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    void unlisten(Consumer<String> l) {
        listeners.remove(l);
    }

    private void changed(String pkg) {
        for (Consumer<String> l : new ArrayList<>(listeners)) l.accept(pkg);
    }
}
