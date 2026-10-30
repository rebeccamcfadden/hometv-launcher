/*
 * The freshness rule and the pruning (oldest first, down to three quarters of the limit) are adapted from
 * cleanyourtv-ui's Tmdb.java and Images.java (https://github.com/matheus-caldeira/cleanyourtv-ui):
 *
 * MIT License
 *
 * Copyright (c) 2026 Gathin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.gathin.tvhome;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * A folder of answers kept on disk, one file per key (a TMDB path or an image URL), bounded in size. A file's
 * modification time is when it was fetched: it is fresh while younger than the time to live its caller gives, and
 * stale after, but a stale copy is still handed out when the network fails, so what was seen once works offline.
 * Writes go to a temporary file first and are renamed into place, so a reader never sees half a file. Pure Java
 * (java.io), unit-tested on the JVM with its own clock (DiskCacheTest).
 */
final class DiskCache {

    /** What the cache holds for a key: its bytes, and whether they are still fresh. */
    static final class Entry {
        final byte[] bytes;
        final boolean fresh;

        Entry(byte[] bytes, boolean fresh) {
            this.bytes = bytes;
            this.fresh = fresh;
        }

        String text() {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /** Files older than this go at the next prune whatever the size: they are long past any time to live. */
    static final long MAX_AGE = 30 * TmdbApi.DAY;

    private final File dir;
    private final long maxBytes;
    private final LongSupplier clock;
    private final Object lock = new Object();
    private long written;

    DiskCache(File dir, long maxBytes, LongSupplier clock) {
        this.dir = dir;
        this.maxBytes = maxBytes;
        this.clock = clock;
    }

    /** The file a key is kept in: its SHA-1 in hex, so any key is a safe file name. */
    File file(String key) {
        return new File(dir, sha1(key));
    }

    /** The cached bytes for key, fresh when younger than ttl, or null when there is nothing (or it can't be read). */
    Entry read(String key, long ttl) {
        File f = file(key);
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            byte[] bytes = readAll(in);
            long age = clock.getAsLong() - f.lastModified();
            return new Entry(bytes, age >= 0 && age < ttl);
        } catch (IOException e) {
            return null;
        }
    }

    /** Keeps bytes under key, now. Prunes once a quarter of the limit has been written since the last prune. */
    void write(String key, byte[] bytes) {
        boolean prune;
        synchronized (lock) {
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            File f = file(key);
            File part = new File(dir, f.getName() + ".part");
            try (FileOutputStream out = new FileOutputStream(part)) {
                out.write(bytes);
            } catch (IOException e) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                return;
            }
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            if (!part.renameTo(f)) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                return;
            }
            //noinspection ResultOfMethodCallIgnored
            f.setLastModified(clock.getAsLong());
            written += bytes.length;
            prune = written > maxBytes / 4;
            if (prune) written = 0;
        }
        if (prune) prune();
    }

    /** Everything the cache holds, in bytes. */
    long size() {
        long total = 0;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) total += f.length();
        return total;
    }

    /**
     * Drops files older than {@link #MAX_AGE} and half-written ones, then, while the folder is over its limit, the
     * oldest until it is down to three quarters of it.
     */
    void prune() {
        synchronized (lock) {
            File[] files = dir.listFiles();
            if (files == null) return;
            long now = clock.getAsLong();
            long total = 0;
            List<File> kept = new ArrayList<>();
            for (File f : files) {
                if (f.getName().endsWith(".part") || now - f.lastModified() > MAX_AGE) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                } else {
                    kept.add(f);
                    total += f.length();
                }
            }
            if (total <= maxBytes) return;
            kept.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (File f : kept) {
                if (total <= maxBytes * 3 / 4) break;
                total -= f.length();
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    static String sha1(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(d.length * 2);
            for (byte b : d) hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
