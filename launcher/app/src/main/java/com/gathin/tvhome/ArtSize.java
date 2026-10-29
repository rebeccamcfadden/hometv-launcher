package com.gathin.tvhome;

/**
 * Sizes for decoding artwork at the size it is shown (Art): pure arithmetic, so it is unit-tested. The art fills its
 * box like CSS's object-fit: cover, so the decoded image has to cover the box in both directions.
 */
final class ArtSize {
    private ArtSize() {}

    /**
     * The largest power-of-two inSampleSize that still leaves the decoded image at least as big as the box in both
     * directions; 1 when the box is unknown (0) or bigger than the source.
     */
    static int sampleSize(int srcW, int srcH, int boxW, int boxH) {
        if (srcW <= 0 || srcH <= 0 || boxW <= 0 || boxH <= 0) return 1;
        int sample = 1;
        while (srcW / (sample * 2) >= boxW && srcH / (sample * 2) >= boxH) sample *= 2;
        return sample;
    }

    /**
     * The smallest size with the source's aspect that covers the box: {width, height}. A source already within a
     * quarter of that is kept as it is (null), since scaling it again would cost more than the memory it saves.
     */
    static int[] cover(int srcW, int srcH, int boxW, int boxH) {
        if (srcW <= 0 || srcH <= 0 || boxW <= 0 || boxH <= 0) return null;
        float s = Math.max(boxW / (float) srcW, boxH / (float) srcH);
        if (s >= 0.8f) return null;
        return new int[]{Math.max(boxW, Math.round(srcW * s)), Math.max(boxH, Math.round(srcH * s))};
    }
}
