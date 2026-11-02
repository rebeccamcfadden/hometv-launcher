package com.hometv.launcher;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Home's memory of which apps open (F15 P2-3): PackageManager is asked once per app until the app changes. */
public class LaunchableTest {

    private final Set<String> installed = new HashSet<>();
    private final List<String> asked = new ArrayList<>();
    private final List<String> named = new ArrayList<>();
    private final Launchable launchable = new Launchable(pkg -> {
        asked.add(pkg);
        return installed.contains(pkg);
    }, pkg -> {
        named.add(pkg);
        return pkg.toUpperCase(Locale.ROOT);
    });

    @Test
    public void asksOnceForEachApp() {
        installed.add("a.one");
        assertTrue(launchable.test("a.one"));
        assertFalse(launchable.test("a.two"));
        assertTrue(launchable.test("a.one"));
        assertFalse(launchable.test("a.two"));
        assertEquals(List.of("a.one", "a.two"), asked);
    }

    @Test
    public void anAppThatChangedIsAskedAgain() {
        assertFalse(launchable.test("a.one"));
        installed.add("a.one");
        assertFalse("remembered until the receiver says it changed", launchable.test("a.one"));
        launchable.forget("a.one");
        assertTrue(launchable.test("a.one"));
        installed.remove("a.one");
        launchable.forget("a.one");
        assertFalse(launchable.test("a.one"));
    }

    @Test
    public void forgettingOneAppKeepsTheOthers() {
        installed.add("a.one");
        installed.add("a.two");
        launchable.test("a.one");
        launchable.test("a.two");
        launchable.forget("a.one");
        asked.clear();
        launchable.test("a.one");
        launchable.test("a.two");
        assertEquals(List.of("a.one"), asked);
    }

    @Test
    public void forgetAllAsksEveryAppAgain() {
        launchable.test("a.one");
        launchable.test("a.two");
        launchable.forgetAll();
        asked.clear();
        launchable.test("a.one");
        launchable.test("a.two");
        assertEquals(List.of("a.one", "a.two"), asked);
    }

    @Test
    public void namesAreRememberedAndForgottenWithTheApp() {
        assertEquals("A.ONE", launchable.label("a.one"));
        assertEquals("A.ONE", launchable.label("a.one"));
        launchable.label("a.two");
        launchable.forget("a.one");
        launchable.label("a.one");
        launchable.label("a.two");
        assertEquals(List.of("a.one", "a.two", "a.one"), named);
        launchable.forgetAll();
        launchable.label("a.two");
        assertEquals(List.of("a.one", "a.two", "a.one", "a.two"), named);
    }

    @Test
    public void listenersHearWhichAppChangedUntilTheyStop() {
        List<String> heard = new ArrayList<>();
        Consumer<String> home = heard::add;
        launchable.listen(home);
        launchable.listen(home);
        launchable.forget("a.one");
        launchable.forgetAll();
        assertEquals("listening twice still hears once; null is any app", Arrays.asList("a.one", null), heard);
        launchable.unlisten(home);
        launchable.forget("a.one");
        assertEquals(2, heard.size());
    }

    @Test
    public void aListenerMayStopListeningWhileItIsTold() {
        int[] heard = {0};
        @SuppressWarnings("unchecked")
        Consumer<String>[] self = new Consumer[1];
        self[0] = pkg -> {
            heard[0]++;
            launchable.unlisten(self[0]);
        };
        launchable.listen(self[0]);
        launchable.forget("a.one");
        launchable.forget("a.one");
        assertEquals(1, heard[0]);
    }
}
