package com.hometv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/** A phone's request waits for the TV's main thread; a "no" after the wait must stay a no. */
public class PhoneRemoteMainWaitTest {
    @Test public void workThatRunsInTimeAnswers() {
        assertEquals("yes", PhoneRemote.runOrGiveUp(Runnable::run, () -> "yes", "or", 1000));
    }

    @Test public void workPostedButNotStartedInTimeNeverRuns() {
        List<Runnable> queued = new ArrayList<>();
        AtomicBoolean ran = new AtomicBoolean();
        String got = PhoneRemote.runOrGiveUp(queued::add, () -> {
            ran.set(true);
            return "changed";
        }, "or", 20);
        assertEquals("or", got);
        // The busy main thread gets to it later: My list must not change after the phone was told it didn't.
        queued.get(0).run();
        assertFalse(ran.get());
    }

    @Test public void workThatStartedAsTheWaitEndedIsWaitedFor() {
        CountDownLatch started = new CountDownLatch(1);
        // The main thread picks it up at once but takes longer than the wait.
        String got = PhoneRemote.runOrGiveUp(r -> {
            new Thread(r).start();
            try {
                started.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, () -> {
            started.countDown();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "changed";
        }, "or", 1);
        // It couldn't be stopped, so its answer is the truth.
        assertEquals("changed", got);
    }
}
