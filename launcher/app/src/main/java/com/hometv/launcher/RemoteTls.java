package com.hometv.launcher;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.X509ExtendedKeyManager;

/**
 * The phone remote's TLS identity (docs/phone-remote.md, HTTPS). The private key is an EC P-256 key in the Android
 * Keystore: it never leaves it, and no crypto library is needed. The certificate is written by {@link RemoteCert} and
 * signed with that key, naming the address the phone connected to, so iPhones accept it after the one-time warning;
 * it is kept in a file so phones keep the exception they accepted, and remade only for a new address or near expiry.
 */
final class RemoteTls {
    static final String ALIAS = "phone_remote_tls";
    static final String CERT_FILE = "phone_remote_cert.der";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final int MAX_ADDRESSES = 8;

    private final File certFile;
    private X509Certificate cert;

    private RemoteTls(File certFile) {
        this.certFile = certFile;
    }

    /** A server socket factory for the TV's identity, in {@code dir}; makes the key the first time. */
    static SSLServerSocketFactory serverSocketFactory(File dir) throws GeneralSecurityException, IOException {
        KeyStore store = keyStore();
        if (!store.containsAlias(ALIAS)) generate();
        PrivateKey key = (PrivateKey) store.getKey(ALIAS, null);
        X509Certificate keystoreCert = (X509Certificate) store.getCertificate(ALIAS);
        if (key == null || keystoreCert == null) throw new GeneralSecurityException("no phone remote key");
        RemoteTls tls = new RemoteTls(new File(dir, CERT_FILE));
        tls.cert = tls.load(keystoreCert.getPublicKey());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(new KeyManager[] {tls.new Keys(key, keystoreCert.getPublicKey())}, null, new SecureRandom());
        return context.getServerSocketFactory();
    }

    /** The certificate phones were last shown, or null. */
    static X509Certificate certificate(File dir) {
        try {
            return parse(Files.readAllBytes(new File(dir, CERT_FILE).toPath()));
        } catch (IOException | GeneralSecurityException e) {
            return null;
        }
    }

    /** Forget all: a new key and certificate next time, so every phone sees the warning again. */
    static void forget(File dir) {
        try {
            keyStore().deleteEntry(ALIAS);
        } catch (GeneralSecurityException | IOException ignored) {
            // Nothing stored, nothing to forget.
        }
        //noinspection ResultOfMethodCallIgnored
        new File(dir, CERT_FILE).delete();
    }

    /** The certificate's SHA-256 fingerprint, "AB:CD:…", as browsers show it. */
    static String fingerprint(X509Certificate cert) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            StringBuilder b = new StringBuilder(digest.length * 3);
            for (byte d : digest) {
                if (b.length() > 0) b.append(':');
                b.append(String.format(Locale.ROOT, "%02X", d & 0xff));
            }
            return b.toString();
        } catch (GeneralSecurityException e) {
            return "";
        }
    }

    /** True when {@code cert} names {@code address} and has a month or more to run. */
    static boolean covers(X509Certificate cert, InetAddress address, long now) {
        if (cert == null || cert.getNotAfter().getTime() - now < 30 * DAY_MS) return false;
        return address == null || addresses(cert).contains(address);
    }

    static List<InetAddress> addresses(X509Certificate cert) {
        List<InetAddress> out = new ArrayList<>();
        try {
            Collection<List<?>> names = cert.getSubjectAlternativeNames();
            if (names == null) return out;
            for (List<?> n : names) {
                if (Integer.valueOf(7).equals(n.get(0))) out.add(InetAddress.getByName((String) n.get(1)));
            }
        } catch (GeneralSecurityException | IOException ignored) {
            // A certificate we can't read names nothing.
        }
        return out;
    }

    private X509Certificate load(PublicKey key) {
        X509Certificate c = certificate(certFile.getParentFile());
        return c != null && c.getPublicKey().equals(key) ? c : null;
    }

    /** The saved certificate if it names {@code address}, else a new one that adds it; called per handshake. */
    private synchronized X509Certificate certFor(InetAddress address, PrivateKey key, PublicKey publicKey) {
        long now = System.currentTimeMillis();
        if (covers(cert, address, now)) return cert;
        List<InetAddress> names = new ArrayList<>();
        if (address != null) names.add(address);
        if (cert != null) {
            for (InetAddress a : addresses(cert)) if (!names.contains(a) && names.size() < MAX_ADDRESSES) names.add(a);
        }
        try {
            byte[] der = RemoteCert.build(publicKey, data -> {
                Signature s = Signature.getInstance("SHA256withECDSA");
                s.initSign(key);
                s.update(data);
                return s.sign();
            }, "HomeTV Launcher", names, null, new BigInteger(63, new SecureRandom()).add(BigInteger.ONE),
                    new Date(now - DAY_MS), new Date(now + RemoteCert.LIFETIME_DAYS * DAY_MS));
            X509Certificate made = parse(der);
            File tmp = new File(certFile.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(der);
            }
            if (!tmp.renameTo(certFile)) throw new IOException("can't save the certificate");
            cert = made;
        } catch (GeneralSecurityException | IOException e) {
            // Keep serving the old certificate: a warning beats no remote.
        }
        return cert;
    }

    private static X509Certificate parse(byte[] der) throws GeneralSecurityException {
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
    }

    private static KeyStore keyStore() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }

    private static void generate() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
        // DIGEST_NONE: TLS signs a digest it has already computed (NONEwithECDSA); SHA-256 signs the certificate.
        generator.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384,
                        KeyProperties.DIGEST_SHA512)
                .build());
        generator.generateKeyPair();
    }

    /** Hands the Keystore key and the certificate for the connection's address to the TLS server. */
    private final class Keys extends X509ExtendedKeyManager {
        private final PrivateKey key;
        private final PublicKey publicKey;
        private volatile X509Certificate chosen;

        Keys(PrivateKey key, PublicKey publicKey) {
            this.key = key;
            this.publicKey = publicKey;
        }

        @Override public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            if (keyType == null || !keyType.startsWith("EC")) return null;
            chosen = certFor(socket == null ? null : socket.getLocalAddress(), key, publicKey);
            return chosen == null ? null : ALIAS;
        }

        @Override public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            if (keyType == null || !keyType.startsWith("EC")) return null;
            chosen = certFor(null, key, publicKey);
            return chosen == null ? null : ALIAS;
        }

        @Override public String[] getServerAliases(String keyType, Principal[] issuers) {
            return new String[] {ALIAS};
        }

        @Override public X509Certificate[] getCertificateChain(String alias) {
            X509Certificate c = chosen != null ? chosen : cert;
            return ALIAS.equals(alias) && c != null ? new X509Certificate[] {c} : null;
        }

        @Override public PrivateKey getPrivateKey(String alias) {
            return ALIAS.equals(alias) ? key : null;
        }

        @Override public String[] getClientAliases(String keyType, Principal[] issuers) {
            return null;
        }

        @Override public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            return null;
        }
    }
}
