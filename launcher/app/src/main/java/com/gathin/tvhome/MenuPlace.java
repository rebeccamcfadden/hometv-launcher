package com.gathin.tvhome;

/**
 * Where the hold-OK tile menu goes (TileMenu): under the tile, else over it, else beside it, always inside the
 * title-safe area and never above a ceiling such as the bottom of Home's greeting, date and clock. Pure, so it is
 * unit-tested (MenuPlaceTest).
 */
final class MenuPlace {
    enum Side { BELOW, ABOVE, RIGHT, LEFT }

    final int left, top;
    final Side side;

    private MenuPlace(int left, int top, Side side) {
        this.left = left;
        this.top = top;
        this.side = side;
    }

    /**
     * @param tile    the tile's {left, top, right, bottom} on the stage
     * @param ceiling the highest y the menu may reach (e.g. under Home's hero), or 0 for the safe area alone
     */
    static MenuPlace of(float[] tile, int pw, int ph, int stageW, int stageH, int safeX, int safeY, int gap,
                        int ceiling) {
        int tl = Math.round(tile[0]), tt = Math.round(tile[1]), tr = Math.round(tile[2]), tb = Math.round(tile[3]);
        int minTop = Math.max(safeY, ceiling);
        int maxBottom = stageH - safeY;
        int maxLeft = stageW - safeX - pw;
        int under = Math.max(safeX, Math.min(tl, maxLeft));
        if (tb + gap + ph <= maxBottom) return new MenuPlace(under, tb + gap, Side.BELOW);
        if (tt - gap - ph >= minTop) return new MenuPlace(under, tt - gap - ph, Side.ABOVE);
        // Beside the tile, level with its middle, pushed down under the ceiling and up into the safe area.
        int top = Math.max(safeY, Math.min(Math.max((tt + tb - ph) / 2, minTop), maxBottom - ph));
        if (tr + gap <= maxLeft) return new MenuPlace(Math.max(safeX, tr + gap), top, Side.RIGHT);
        if (tl - gap - pw >= safeX) return new MenuPlace(Math.min(tl - gap - pw, maxLeft), top, Side.LEFT);
        // No room anywhere: over the tile, still under the ceiling and inside the safe area.
        int over = Math.max(safeY, Math.min(Math.max(tt - gap - ph, minTop), maxBottom - ph));
        return new MenuPlace(under, over, Side.ABOVE);
    }

    /**
     * Where focus goes when the menu closes and its tile has gone (a title the menu took off the list a row shows):
     * the other keys of the tile's row or grid, nearest first, the one after it before the one before it. Empty when
     * {@code key} isn't in {@code row}.
     */
    static java.util.List<String> nearest(java.util.List<String> row, String key) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int i = row.indexOf(key);
        if (i < 0) return out;
        for (int d = 1; d < row.size(); d++) {
            if (i + d < row.size()) out.add(row.get(i + d));
            if (i - d >= 0) out.add(row.get(i - d));
        }
        return out;
    }
}
