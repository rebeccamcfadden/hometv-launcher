package com.hometv.launcher;

/** Pixel checks for AppArt, kept apart so they can be unit-tested on the JVM. */
final class ArtPixels {
    private ArtPixels() {}

    /** Whether every ARGB pixel is fully opaque (alpha 255). */
    static boolean opaque(int[] argb) {
        for (int p : argb) if ((p >>> 24) != 0xFF) return false;
        return true;
    }
}
