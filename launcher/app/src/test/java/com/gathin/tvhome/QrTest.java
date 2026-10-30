package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Qr against Project Nayuki's own encoder: the vectors were made with his Python library (qrcodegen 1.8, PyPI),
 * {@code encode_segments([make_bytes(text)], ecl, 1, 40, mask, boost)}. Each gives the version, level and mask he
 * chose and the SHA-256 of the modules read row by row as "1" for dark and "0" for light.
 */
public class QrTest {
    private static final String ALPHA = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.";
    /** A stand-in for TMDB's v4 request token, as long as a real one; not a token. */
    private static final String TOKEN = repeat(ALPHA, 4).substring(0, 250);
    private static final String V4_URL = "https://www.themoviedb.org/auth/access?request_token=" + TOKEN;
    private static final String V3_URL = "https://www.themoviedb.org/authenticate/0123456789abcdef0123456789abcdef01234567";

    @Test
    public void helloWorldMatchesModuleForModule() {
        String[] expected = {
            "#######...###.#######",
            "#.....#.###.#.#.....#",
            "#.###.#...###.#.###.#",
            "#.###.#.##..#.#.###.#",
            "#.###.#..#..#.#.###.#",
            "#.....#.#..#..#.....#",
            "#######.#.#.#.#######",
            ".........#...........",
            "#####.###..#.#.#.#.#.",
            "#...##..#.#.##..###.#",
            "##.##.#.#.#.###..###.",
            ".##..#.##..###.#.##..",
            "..#####.#...#.##....#",
            "........##...#####...",
            "#######.#...####..##.",
            "#.....#..#..##.#.###.",
            "#.###.#.#.#####.#..##",
            "#.###.#.###....###...",
            "#.###.#.#..##.##..#..",
            "#.....#.###.##..###..",
            "#######.##.#..#.#..#.",
        };
        Qr q = Qr.encode("Hello, world!", Qr.Ecc.LOW, 2, false);
        assertEquals(1, q.version);
        assertEquals(21, q.size);
        assertArrayEquals(expected, rows(q));
    }

    @Test
    public void helloWorldAutomatic() {
        check(Qr.encode("Hello, world!", Qr.Ecc.MEDIUM, -1, true), 1, Qr.Ecc.MEDIUM, 2,
                "9ec093e9c8e6ca1bb4c4a28edde04d1b1c96221e49a45ee3e26f278397d05d72");
    }

    @Test
    public void v3SignInLink() {
        check(Qr.encode(V3_URL), 5, Qr.Ecc.MEDIUM, 2,
                "89575d6f5166d9769dc8a58c400d96a5f83a02d1eedb5cc59ac057953f66a431");
    }

    @Test
    public void v4SignInLink() {
        assertEquals(303, V4_URL.length());
        check(Qr.encode(V4_URL), 13, Qr.Ecc.MEDIUM, 3,
                "2258b78bcccf856a184ed1c5b9b86b5833c53724d7e18a12270db6db04c35fb4");
    }

    @Test
    public void fixedMaskAndHighLevelWithVersionInformation() {
        check(Qr.encode(V4_URL, Qr.Ecc.HIGH, 5, false), 18, Qr.Ecc.HIGH, 5,
                "1609818257b58aa784494a6bf1c365f974f991f196b94aae4de569482ab6cc86");
    }

    @Test
    public void utf8IsRaisedToQuartileStill() {
        check(Qr.encode("Sm\u00f6rg\u00e5sbord \u2615", Qr.Ecc.QUARTILE, -1, true), 2, Qr.Ecc.QUARTILE, 2,
                "e1782d23e7ca5b51b3af1f9c158cbfba99dda3310b1591f4ef70069e987d7966");
    }

    @Test
    public void sixteenBitLengthFromVersionTen() {
        check(Qr.encode(repeat("x", 1000), Qr.Ecc.LOW, -1, true), 22, Qr.Ecc.LOW, 0,
                "99f475e4071e6b7428bd675d21f44530883326c98290d86edf4bf3c63c6a8e71");
    }

    @Test
    public void largestVersion() {
        check(Qr.encode(repeat("a", 2953), Qr.Ecc.LOW, -1, true), 40, Qr.Ecc.LOW, 1,
                "c27a04b5479583f147f7a50ff0707d1cd63ee3f0a5af3365cdd06bb9091adb1e");
    }

    @Test
    public void emptyTextIsRaisedToHigh() {
        check(Qr.encode(""), 1, Qr.Ecc.HIGH, 6,
                "eda56944c6388497decff5606742aa3abb818ced31a14a4b7d67a957c8c2584d");
    }

    @Test
    public void tooLongThrows() {
        try {
            Qr.encode(repeat("a", 2954), Qr.Ecc.LOW, -1, true);
            fail();
        } catch (IllegalArgumentException expected) {
            // Over version 40's capacity at level L.
        }
        try {
            Qr.encode(repeat("a", 2332));
            fail();
        } catch (IllegalArgumentException expected) {
            // Over version 40's capacity at level M.
        }
    }

    @Test
    public void matrixIsACopyAndOutsideIsLight() {
        Qr q = Qr.encode(V3_URL);
        boolean[][] m = q.matrix();
        assertEquals(q.size, m.length);
        assertTrue(m[0][0]);
        m[0][0] = false;
        assertTrue(q.dark(0, 0));
        assertFalse(q.dark(-1, 0));
        assertFalse(q.dark(0, q.size));
    }

    @Test
    public void badMaskThrows() {
        try {
            Qr.encode("x", Qr.Ecc.LOW, 8, false);
            fail();
        } catch (IllegalArgumentException expected) {
            // Masks are 0 to 7, or -1 for the best.
        }
    }

    private static void check(Qr q, int version, Qr.Ecc ecc, int mask, String sha) {
        assertEquals("version", version, q.version);
        assertEquals("size", version * 4 + 17, q.size);
        assertEquals("level", ecc, q.ecc);
        assertEquals("mask", mask, q.mask);
        assertEquals("modules", sha, sha256(bits(q)));
    }

    private static String bits(Qr q) {
        StringBuilder b = new StringBuilder(q.size * q.size);
        for (int y = 0; y < q.size; y++) for (int x = 0; x < q.size; x++) b.append(q.dark(x, y) ? '1' : '0');
        return b.toString();
    }

    private static String[] rows(Qr q) {
        String[] out = new String[q.size];
        for (int y = 0; y < q.size; y++) {
            StringBuilder b = new StringBuilder();
            for (int x = 0; x < q.size; x++) b.append(q.dark(x, y) ? '#' : '.');
            out[y] = b.toString();
        }
        return out;
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.US_ASCII));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x & 0xFF));
            return b.toString();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(s);
        return b.toString();
    }
}
