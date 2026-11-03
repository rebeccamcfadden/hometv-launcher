package com.hometv.launcher;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Writes the phone remote's self-signed certificate in DER (RFC 5280), because the Keystore's own certificate has no
 * subjectAltName or serverAuth usage and a ten-year life, which iOS 13 and later refuse for any TLS server
 * (support.apple.com/103769). Pure Java so it is unit-tested on the JVM; the signature comes from the caller's key.
 */
final class RemoteCert {
    /** Days a certificate lives: under iOS's 825-day ceiling. */
    static final int LIFETIME_DAYS = 800;

    interface Signer {
        /** An ECDSA-with-SHA-256 signature over {@code data}, DER-encoded as Signature returns it. */
        byte[] sign(byte[] data) throws GeneralSecurityException;
    }

    private static final byte[] OID_ECDSA_SHA256 = oid(1, 2, 840, 10045, 4, 3, 2);
    private static final byte[] OID_COMMON_NAME = oid(2, 5, 4, 3);
    private static final byte[] OID_KEY_USAGE = oid(2, 5, 29, 15);
    private static final byte[] OID_SUBJECT_ALT_NAME = oid(2, 5, 29, 17);
    private static final byte[] OID_BASIC_CONSTRAINTS = oid(2, 5, 29, 19);
    private static final byte[] OID_EXT_KEY_USAGE = oid(2, 5, 29, 37);
    private static final byte[] OID_SERVER_AUTH = oid(1, 3, 6, 1, 5, 5, 7, 3, 1);

    private RemoteCert() {}

    /** The certificate for {@code key}, naming {@code addresses} (the TV's own) and {@code dnsName} if set. */
    static byte[] build(PublicKey key, Signer signer, String commonName, List<InetAddress> addresses, String dnsName,
            BigInteger serial, Date notBefore, Date notAfter) throws GeneralSecurityException {
        byte[] name = seq(set(seq(OID_COMMON_NAME, tlv(0x0c, commonName.getBytes(StandardCharsets.UTF_8)))));
        byte[] algorithm = seq(OID_ECDSA_SHA256);
        ByteArrayOutputStream altNames = new ByteArrayOutputStream();
        if (dnsName != null && !dnsName.isEmpty()) write(altNames, tlv(0x82, dnsName.getBytes(StandardCharsets.US_ASCII)));
        for (InetAddress a : addresses) write(altNames, tlv(0x87, a.getAddress()));
        byte[] extensions = seq(
                extension(OID_BASIC_CONSTRAINTS, true, seq()),
                extension(OID_KEY_USAGE, true, tlv(0x03, new byte[] {0x07, (byte) 0x80})),
                extension(OID_EXT_KEY_USAGE, false, seq(OID_SERVER_AUTH)),
                extension(OID_SUBJECT_ALT_NAME, false, tlv(0x30, altNames.toByteArray())));
        byte[] tbs = seq(
                tlv(0xa0, integer(BigInteger.valueOf(2))),
                integer(serial),
                algorithm,
                name,
                seq(time(notBefore), time(notAfter)),
                name,
                key.getEncoded(),
                tlv(0xa3, extensions));
        byte[] signature = signer.sign(tbs);
        byte[] bits = new byte[signature.length + 1];
        System.arraycopy(signature, 0, bits, 1, signature.length);
        return seq(tbs, algorithm, tlv(0x03, bits));
    }

    private static byte[] extension(byte[] oid, boolean critical, byte[] value) {
        return critical ? seq(oid, new byte[] {0x01, 0x01, (byte) 0xff}, tlv(0x04, value)) : seq(oid, tlv(0x04, value));
    }

    /** UTCTime before 2050, GeneralizedTime from then on (RFC 5280, 4.1.2.5). */
    private static byte[] time(Date d) {
        SimpleDateFormat utc = new SimpleDateFormat("yyyy", Locale.ROOT);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        boolean generalized = Integer.parseInt(utc.format(d)) >= 2050;
        utc.applyPattern(generalized ? "yyyyMMddHHmmss'Z'" : "yyMMddHHmmss'Z'");
        return tlv(generalized ? 0x18 : 0x17, utc.format(d).getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] integer(BigInteger v) {
        return tlv(0x02, v.toByteArray());
    }

    private static byte[] oid(int... arcs) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(arcs[0] * 40 + arcs[1]);
        for (int i = 2; i < arcs.length; i++) {
            int v = arcs[i];
            int shift = 28;
            while (shift > 0 && (v >>> shift) == 0) shift -= 7;
            for (; shift > 0; shift -= 7) b.write(0x80 | ((v >>> shift) & 0x7f));
            b.write(v & 0x7f);
        }
        return tlv(0x06, b.toByteArray());
    }

    private static byte[] seq(byte[]... parts) {
        return tlv(0x30, concat(parts));
    }

    private static byte[] set(byte[]... parts) {
        return tlv(0x31, concat(parts));
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (byte[] p : parts) write(b, p);
        return b.toByteArray();
    }

    static byte[] tlv(int tag, byte[] value) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(value.length + 6);
        b.write(tag);
        int n = value.length;
        if (n < 0x80) {
            b.write(n);
        } else {
            int bytes = n > 0xffffff ? 4 : n > 0xffff ? 3 : n > 0xff ? 2 : 1;
            b.write(0x80 | bytes);
            for (int i = bytes - 1; i >= 0; i--) b.write((n >>> (8 * i)) & 0xff);
        }
        write(b, value);
        return b.toByteArray();
    }

    private static void write(ByteArrayOutputStream b, byte[] bytes) {
        b.write(bytes, 0, bytes.length);
    }
}
