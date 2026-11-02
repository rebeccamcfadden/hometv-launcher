package com.hometv.launcher;

import java.util.List;

/**
 * Which rows of the Movies and TV Shows grid hold their pictures (F15 P1-2): rows within a screen of the visible part
 * bind their art, rows more than two screens away let go of it, and rows between keep what they have, so scrolling
 * back and forth near the edge doesn't bind and release the same row over and over. Pure, for the tests.
 */
final class ArtWindow {
    private ArtWindow() {}

    enum Act { BIND, RELEASE, KEEP }

    /** A row from top to bottom, in the scrolling box's visible coordinates, with the box height tall. */
    static Act decide(float top, float bottom, int height) {
        if (height <= 0) return Act.KEEP;
        if (bottom >= -height && top <= 2f * height) return Act.BIND;
        if (bottom < -2f * height || top > 3f * height) return Act.RELEASE;
        return Act.KEEP;
    }

    /** Whether two lists name the same titles in the same order: the grid can then be rebound in place (P2-2). */
    static boolean sameIds(List<Title> a, List<Title> b) {
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!a.get(i).id.equals(b.get(i).id)) return false;
        return true;
    }
}
