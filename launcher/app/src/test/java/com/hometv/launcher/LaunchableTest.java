package com.hometv.launcher;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Home's memory of which apps open (F15 P2-3): PackageManager is asked once per app until the app changes. */
public class LaunchableTest {

    private final Set<String> installed = new HashSet<>();
    private final List<String> asked = new ArrayList<>();
    private final Launchable launchable = new Launchable(pkg -> {
        asked.add(pkg);
        return installed.contains(pkg);
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
    public void listenersHearEachChangeUntilTheyStop() {
        int[] heard = {0};
        Runnable home = () -> heard[0]++;
        launchable.listen(home);
        launchable.listen(home);
        launchable.forget("a.one");
        launchable.forgetAll();
        assertEquals("listening twice still hears once", 2, heard[0]);
        launchable.unlisten(home);
        launchable.forget("a.one");
        assertEquals(2, heard[0]);
    }

    @Test
    public void aListenerMayStopListeningWhileItIsTold() {
        int[] heard = {0};
        Runnable[] self = new Runnable[1];
        self[0] = () -> {
            heard[0]++;
            launchable.unlisten(self[0]);
        };
        launchable.listen(self[0]);
        launchable.forget("a.one");
        launchable.forget("a.one");
        assertEquals(1, heard[0]);
    }
}
