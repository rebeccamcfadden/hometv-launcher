package com.hometv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class RemoteCertTest {
    private static final long DAY = 24L * 60 * 60 * 1000;

    private static X509Certificate make(KeyPair pair, List<InetAddress> ips, String dns, Date from, Date to)
            throws Exception {
        byte[] der = RemoteCert.build(pair.getPublic(), data -> {
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initSign(pair.getPrivate());
            s.update(data);
            return s.sign();
        }, "HomeTV Launcher", ips, dns, new BigInteger("1234567890123456789"), from, to);
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
    }

    private static KeyPair ecKey() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }

    @Test public void aServerCertificateIOSAccepts() throws Exception {
        KeyPair pair = ecKey();
        Date from = new Date(1_790_000_000_000L);
        Date to = new Date(from.getTime() + RemoteCert.LIFETIME_DAYS * DAY);
        X509Certificate c = make(pair, Arrays.asList(InetAddress.getByName("192.168.1.50"),
                InetAddress.getByName("fe80::1")), "hometv.local", from, to);

        c.verify(pair.getPublic());
        assertEquals(3, c.getVersion());
        assertEquals("SHA256withECDSA", c.getSigAlgName());
        assertEquals("CN=HomeTV Launcher", c.getSubjectX500Principal().getName());
        assertEquals(c.getSubjectX500Principal(), c.getIssuerX500Principal());
        assertEquals(new BigInteger("1234567890123456789"), c.getSerialNumber());
        assertEquals(from.getTime() / 1000, c.getNotBefore().getTime() / 1000);
        assertEquals(to.getTime() / 1000, c.getNotAfter().getTime() / 1000);
        assertTrue(RemoteCert.LIFETIME_DAYS <= 825);
        assertEquals(-1, c.getBasicConstraints());
        assertTrue(c.getKeyUsage()[0]);
        assertTrue(c.getExtendedKeyUsage().contains("1.3.6.1.5.5.7.3.1"));
        assertTrue(c.getCriticalExtensionOIDs().contains("2.5.29.15"));
        assertFalse(c.getCriticalExtensionOIDs().contains("2.5.29.17"));

        List<String> names = new ArrayList<>();
        for (List<?> n : c.getSubjectAlternativeNames()) names.add(n.get(0) + "=" + n.get(1));
        assertTrue(names.toString(), names.contains("2=hometv.local"));
        assertTrue(names.toString(), names.contains("7=192.168.1.50"));
        assertEquals(3, names.size());
    }

    @Test public void datesFrom2050AreGeneralizedTime() throws Exception {
        KeyPair pair = ecKey();
        Date from = new Date(2_524_608_000_000L - 10 * DAY); // late 2049
        Date to = new Date(from.getTime() + RemoteCert.LIFETIME_DAYS * DAY);
        X509Certificate c = make(pair, Arrays.asList(InetAddress.getByName("10.0.0.2")), null, from, to);
        c.verify(pair.getPublic());
        assertEquals(to.getTime() / 1000, c.getNotAfter().getTime() / 1000);
        Collection<List<?>> alt = c.getSubjectAlternativeNames();
        assertEquals(1, alt.size());
    }

    @Test public void longLengthsUseTheLongForm() {
        byte[] v = new byte[300];
        byte[] t = RemoteCert.tlv(0x04, v);
        assertEquals(0x04, t[0]);
        assertEquals((byte) 0x82, t[1]);
        assertEquals(1, t[2]);
        assertEquals(44, t[3]);
        assertEquals(304, t.length);
    }
}
