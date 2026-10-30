/*
 * Adapted from Project Nayuki's QR Code generator library (Java), by way of cleanyourtv-ui's Qr.java.
 *
 * Copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/qr-code-generator-library
 *
 * Copyright (c) 2026 Matheus Caldeira (MIT License)
 * https://github.com/matheus-caldeira/cleanyourtv-ui
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * - The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 * - The Software is provided "as is", without warranty of any kind, express or
 *   implied, including but not limited to the warranties of merchantability,
 *   fitness for a particular purpose and noninfringement. In no event shall the
 *   authors or copyright holders be liable for any claim, damages or other
 *   liability, whether in an action of contract, tort or otherwise, arising from,
 *   out of or in connection with the Software or the use or other dealings in the
 *   Software.
 */

package com.gathin.tvhome;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A QR code for the TMDB sign-in link (BACKLOG.md, TMDB sign-in and watchlist sync): the text as UTF-8 in byte mode,
 * versions 1 to 40, the smallest version that fits, the error correction raised while the version stays the same,
 * and the mask with the lowest penalty, exactly as Project Nayuki's encoder chooses them (QrTest compares its output
 * with his). Pure Java; the screen that shows it draws {@link #dark} as squares with a light border of 4 modules.
 */
final class Qr {
    /** Error correction, from the least to the most; the order matters to the tables and to raising the level. */
    enum Ecc {
        LOW(1), MEDIUM(0), QUARTILE(3), HIGH(2);

        final int formatBits;

        Ecc(int formatBits) {
            this.formatBits = formatBits;
        }
    }

    static final int MIN_VERSION = 1, MAX_VERSION = 40;

    final int version;
    /** Modules per side: version × 4 + 17. */
    final int size;
    final Ecc ecc;
    final int mask;
    private final boolean[][] modules;
    private boolean[][] isFunction;

    /** A link at level M or better. Throws IllegalArgumentException when it is too long for any version. */
    static Qr encode(String text) {
        return encode(text, Ecc.MEDIUM, -1, true);
    }

    /**
     * {@code mask} is 0 to 7, or -1 to choose the best; with {@code boost} the level is raised while the data still
     * fits the same version.
     */
    static Qr encode(String text, Ecc ecc, int mask, boolean boost) {
        if (text == null || ecc == null) throw new NullPointerException();
        if (mask < -1 || mask > 7) throw new IllegalArgumentException("mask out of range");
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int version;
        int used = 0;
        for (version = MIN_VERSION; ; version++) {
            used = 4 + (version <= 9 ? 8 : 16) + data.length * 8;
            if (data.length < (version <= 9 ? 1 << 8 : 1 << 16) && used <= dataCodewords(version, ecc) * 8) break;
            if (version >= MAX_VERSION) throw new IllegalArgumentException("text too long for a QR code");
        }
        if (boost) {
            for (Ecc e : Ecc.values()) if (used <= dataCodewords(version, e) * 8) ecc = e;
        }
        int capacity = dataCodewords(version, ecc) * 8;
        Bits bits = new Bits(capacity);
        bits.append(0x4, 4);
        bits.append(data.length, version <= 9 ? 8 : 16);
        for (byte b : data) bits.append(b & 0xFF, 8);
        bits.append(0, Math.min(4, capacity - bits.length));
        bits.append(0, (8 - bits.length % 8) % 8);
        for (int pad = 0xEC; bits.length < capacity; pad ^= 0xEC ^ 0x11) bits.append(pad, 8);
        byte[] codewords = new byte[capacity / 8];
        for (int i = 0; i < bits.length; i++) if (bits.bits[i]) codewords[i >>> 3] |= (byte) (1 << (7 - (i & 7)));
        return new Qr(version, ecc, codewords, mask);
    }

    private Qr(int version, Ecc ecc, byte[] dataCodewords, int msk) {
        this.version = version;
        this.size = version * 4 + 17;
        this.ecc = ecc;
        modules = new boolean[size][size];
        isFunction = new boolean[size][size];
        drawFunctionPatterns();
        drawCodewords(addEccAndInterleave(dataCodewords));
        if (msk == -1) {
            int min = Integer.MAX_VALUE;
            for (int i = 0; i < 8; i++) {
                applyMask(i);
                drawFormatBits(i);
                int penalty = penalty();
                if (penalty < min) {
                    msk = i;
                    min = penalty;
                }
                applyMask(i);
            }
        }
        mask = msk;
        applyMask(msk);
        drawFormatBits(msk);
        isFunction = null;
    }

    /** Whether the module at column x, row y is dark; outside the code is light. */
    boolean dark(int x, int y) {
        return x >= 0 && x < size && y >= 0 && y < size && modules[y][x];
    }

    /** A copy of the modules, rows first: {@code matrix()[y][x]} is true for dark. */
    boolean[][] matrix() {
        boolean[][] out = new boolean[size][];
        for (int y = 0; y < size; y++) out[y] = Arrays.copyOf(modules[y], size);
        return out;
    }

    // ---- Function patterns ---------------------------------------------------------------------------------------

    private void drawFunctionPatterns() {
        for (int i = 0; i < size; i++) {
            setFunction(6, i, i % 2 == 0);
            setFunction(i, 6, i % 2 == 0);
        }
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);
        int[] align = alignmentPositions();
        int n = align.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == 0 && j == 0 || i == 0 && j == n - 1 || i == n - 1 && j == 0) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        setFunction(align[i] + dx, align[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
                    }
                }
            }
        }
        drawFormatBits(0);
        drawVersion();
    }

    private void drawFormatBits(int msk) {
        int data = ecc.formatBits << 3 | msk;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = (data << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) setFunction(8, i, bit(bits, i));
        setFunction(8, 7, bit(bits, 6));
        setFunction(8, 8, bit(bits, 7));
        setFunction(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) setFunction(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) setFunction(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) setFunction(8, size - 15 + i, bit(bits, i));
        setFunction(8, size - 8, true);
    }

    private void drawVersion() {
        if (version < 7) return;
        int rem = version;
        for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        int bits = version << 12 | rem;
        for (int i = 0; i < 18; i++) {
            boolean b = bit(bits, i);
            int a = size - 11 + i % 3, c = i / 3;
            setFunction(a, c, b);
            setFunction(c, a, b);
        }
    }

    private void drawFinder(int x, int y) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int xx = x + dx, yy = y + dy;
                if (xx >= 0 && xx < size && yy >= 0 && yy < size) setFunction(xx, yy, dist != 2 && dist != 4);
            }
        }
    }

    private void setFunction(int x, int y, boolean dark) {
        modules[y][x] = dark;
        isFunction[y][x] = true;
    }

    private int[] alignmentPositions() {
        if (version == 1) return new int[0];
        int n = version / 7 + 2;
        int step = (version * 8 + n * 3 + 5) / (n * 4 - 4) * 2;
        int[] out = new int[n];
        out[0] = 6;
        for (int i = n - 1, pos = size - 7; i >= 1; i--, pos -= step) out[i] = pos;
        return out;
    }

    // ---- Codewords and masking -----------------------------------------------------------------------------------

    private byte[] addEccAndInterleave(byte[] data) {
        int blocks = BLOCKS[ecc.ordinal()][version];
        int eccLen = ECC_PER_BLOCK[ecc.ordinal()][version];
        int raw = rawModules(version) / 8;
        int shortBlocks = blocks - raw % blocks;
        int shortLen = raw / blocks;
        byte[][] all = new byte[blocks][];
        byte[] divisor = rsDivisor(eccLen);
        for (int i = 0, k = 0; i < blocks; i++) {
            byte[] dat = Arrays.copyOfRange(data, k, k + shortLen - eccLen + (i < shortBlocks ? 0 : 1));
            k += dat.length;
            byte[] block = Arrays.copyOf(dat, shortLen + 1);
            byte[] rem = rsRemainder(dat, divisor);
            System.arraycopy(rem, 0, block, block.length - eccLen, rem.length);
            all[i] = block;
        }
        byte[] out = new byte[raw];
        for (int i = 0, k = 0; i < all[0].length; i++) {
            for (int j = 0; j < blocks; j++) {
                // The short blocks have no byte at this index.
                if (i != shortLen - eccLen || j >= shortBlocks) out[k++] = all[j][i];
            }
        }
        return out;
    }

    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!isFunction[y][x] && i < data.length * 8) {
                        modules[y][x] = bit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void applyMask(int msk) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean invert;
                switch (msk) {
                    case 0: invert = (x + y) % 2 == 0; break;
                    case 1: invert = y % 2 == 0; break;
                    case 2: invert = x % 3 == 0; break;
                    case 3: invert = (x + y) % 3 == 0; break;
                    case 4: invert = (x / 3 + y / 2) % 2 == 0; break;
                    case 5: invert = x * y % 2 + x * y % 3 == 0; break;
                    case 6: invert = (x * y % 2 + x * y % 3) % 2 == 0; break;
                    default: invert = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
                }
                modules[y][x] ^= invert & !isFunction[y][x];
            }
        }
    }

    /** The standard's penalty for the current modules; the mask with the lowest wins. */
    private int penalty() {
        int result = 0;
        for (int y = 0; y < size; y++) {
            boolean runColor = false;
            int run = 0;
            int[] history = new int[7];
            for (int x = 0; x < size; x++) {
                if (modules[y][x] == runColor) {
                    run++;
                    if (run == 5) result += N1;
                    else if (run > 5) result++;
                } else {
                    addHistory(run, history);
                    if (!runColor) result += countFinderLike(history) * N3;
                    runColor = modules[y][x];
                    run = 1;
                }
            }
            result += terminateAndCount(runColor, run, history) * N3;
        }
        for (int x = 0; x < size; x++) {
            boolean runColor = false;
            int run = 0;
            int[] history = new int[7];
            for (int y = 0; y < size; y++) {
                if (modules[y][x] == runColor) {
                    run++;
                    if (run == 5) result += N1;
                    else if (run > 5) result++;
                } else {
                    addHistory(run, history);
                    if (!runColor) result += countFinderLike(history) * N3;
                    runColor = modules[y][x];
                    run = 1;
                }
            }
            result += terminateAndCount(runColor, run, history) * N3;
        }
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = modules[y][x];
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) result += N2;
            }
        }
        int dark = 0;
        for (boolean[] row : modules) for (boolean c : row) if (c) dark++;
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        return result + k * N4;
    }

    private int countFinderLike(int[] h) {
        int n = h[1];
        boolean core = n > 0 && h[2] == n && h[3] == n * 3 && h[4] == n && h[5] == n;
        return (core && h[0] >= n * 4 && h[6] >= n ? 1 : 0) + (core && h[6] >= n * 4 && h[0] >= n ? 1 : 0);
    }

    private int terminateAndCount(boolean runColor, int run, int[] history) {
        if (runColor) {
            addHistory(run, history);
            run = 0;
        }
        run += size;
        addHistory(run, history);
        return countFinderLike(history);
    }

    private void addHistory(int run, int[] history) {
        if (history[0] == 0) run += size;
        System.arraycopy(history, 0, history, 1, history.length - 1);
        history[0] = run;
    }

    // ---- Arithmetic ----------------------------------------------------------------------------------------------

    private static int rawModules(int ver) {
        int result = (16 * ver + 128) * ver + 64;
        if (ver >= 2) {
            int n = ver / 7 + 2;
            result -= (25 * n - 10) * n - 55;
            if (ver >= 7) result -= 36;
        }
        return result;
    }

    static int dataCodewords(int ver, Ecc e) {
        return rawModules(ver) / 8 - ECC_PER_BLOCK[e.ordinal()][ver] * BLOCKS[e.ordinal()][ver];
    }

    private static byte[] rsDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < result.length; j++) {
                result[j] = (byte) multiply(result[j] & 0xFF, root);
                if (j + 1 < result.length) result[j] ^= result[j + 1];
            }
            root = multiply(root, 0x02);
        }
        return result;
    }

    private static byte[] rsRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) result[i] ^= (byte) multiply(divisor[i] & 0xFF, factor);
        }
        return result;
    }

    /** The product in GF(2^8) modulo 0x11D. */
    private static int multiply(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private static boolean bit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }

    private static final class Bits {
        final boolean[] bits;
        int length;

        Bits(int capacity) {
            bits = new boolean[capacity];
        }

        void append(int value, int count) {
            for (int i = count - 1; i >= 0; i--) bits[length++] = ((value >>> i) & 1) != 0;
        }
    }

    private static final int N1 = 3, N2 = 3, N3 = 40, N4 = 10;

    // Index 0 is padding; the rows are LOW, MEDIUM, QUARTILE, HIGH (the Ecc order).
    private static final byte[][] ECC_PER_BLOCK = {
        {-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30,
            30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},
        {-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28,
            28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28},
        {-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30,
            30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},
        {-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30,
            30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},
    };

    private static final byte[][] BLOCKS = {
        {-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18,
            19, 19, 20, 21, 22, 24, 25},
        {-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29,
            31, 33, 35, 37, 38, 40, 43, 45, 47, 49},
        {-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38,
            40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68},
        {-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45,
            48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81},
    };
}
