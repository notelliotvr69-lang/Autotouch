package com.vrflex.mobile;

import android.content.Context;
import android.os.Build;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1GeneralizedTime;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.PrivateKeyUsagePeriod;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;

/** Real Android 11+ Wireless Debugging ADB pairing and read-only Quest diagnostics. */
final class AdbSession {
    private static AdbSession instance;
    private final PersistentConnectionManager manager;

    static synchronized AdbSession get(Context context) throws Exception {
        if (instance == null) instance = new AdbSession(context.getApplicationContext());
        return instance;
    }

    private AdbSession(Context context) throws Exception {
        manager = new PersistentConnectionManager(context);
        manager.setApi(Build.VERSION.SDK_INT);
        manager.setTimeout(12, TimeUnit.SECONDS);
        manager.setThrowOnUnauthorised(true);
    }

    synchronized boolean pair(String host, int port, String code) throws Exception {
        return manager.pair(host, port, code);
    }

    synchronized boolean connect(String host, int port) throws Exception {
        manager.setTimeout(12, TimeUnit.SECONDS);
        return manager.connect(host, port);
    }

    synchronized String diagnostics() throws Exception {
        if (!manager.isConnected()) throw new IllegalStateException("Not connected to the Quest.");
        // Deliberately read-only: collect basic device properties only.
        String command = "getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk; getprop ro.build.version.name";
        try (AdbStream stream = manager.openStream("shell:" + command);
             InputStream in = stream.openInputStream()) {
            StringBuilder out = new StringBuilder();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (out.length() + count > 8192) break;
                out.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
            }
            return out.toString().trim();
        }
    }

    synchronized void disconnect() throws Exception {
        manager.disconnect();
    }

    private static final class PersistentConnectionManager extends AbsAdbConnectionManager {
        private final PrivateKey privateKey;
        private final Certificate certificate;

        PersistentConnectionManager(Context context) throws Exception {
            File keyFile = new File(context.getFilesDir(), "quest_adb_private.pk8");
            File certFile = new File(context.getFilesDir(), "quest_adb_cert.pem");
            if (keyFile.exists() && certFile.exists()) {
                byte[] encoded = new byte[(int) keyFile.length()];
                try (InputStream in = new FileInputStream(keyFile)) {
                    int offset = 0, n;
                    while (offset < encoded.length && (n = in.read(encoded, offset, encoded.length - offset)) > 0) offset += n;
                }
                privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
                try (InputStream in = new FileInputStream(certFile)) {
                    certificate = CertificateFactory.getInstance("X.509").generateCertificate(in);
                }
            } else {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048, new SecureRandom());
                KeyPair pair = generator.generateKeyPair();
                privateKey = pair.getPrivate();
                certificate = createCertificate(pair.getPublic(), privateKey);
                try (FileOutputStream out = new FileOutputStream(keyFile)) {
                    out.write(privateKey.getEncoded());
                }
                try (JcaPEMWriter writer = new JcaPEMWriter(
                        new OutputStreamWriter(new FileOutputStream(certFile), StandardCharsets.UTF_8))) {
                    writer.writeObject(certificate);
                }
            }
        }

        private static Certificate createCertificate(PublicKey publicKey, PrivateKey privateKey) throws Exception {
            Date start = new Date();
            Date end = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);
            X500Name subject = new X500Name("CN=VRFlex Mobile ADB");
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    subject, BigInteger.valueOf(Math.abs(new SecureRandom().nextLong()) + 1),
                    start, end, subject, publicKey);
            JcaX509ExtensionUtils extensions = new JcaX509ExtensionUtils();
            builder.addExtension(Extension.subjectKeyIdentifier, false,
                    extensions.createSubjectKeyIdentifier(publicKey));
            ASN1EncodableVector period = new ASN1EncodableVector();
            period.add(new DERTaggedObject(false, 0, new ASN1GeneralizedTime(start)));
            period.add(new DERTaggedObject(false, 1, new ASN1GeneralizedTime(end)));
            builder.addExtension(Extension.privateKeyUsagePeriod, false,
                    PrivateKeyUsagePeriod.getInstance(new DERSequence(period)));
            ContentSigner signer = new JcaContentSignerBuilder("SHA512withRSA").build(privateKey);
            return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        }

        @Override protected PrivateKey getPrivateKey() { return privateKey; }
        @Override protected Certificate getCertificate() { return certificate; }
        @Override protected String getDeviceName() { return "VRFlexMobile"; }
    }
}
