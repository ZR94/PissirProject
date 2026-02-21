package upo.pissir.camera.mqtt;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import javax.net.ssl.TrustManagerFactory;

public final class MqttSsl {
    private MqttSsl() {}

    public static SSLSocketFactory socketFactoryFromCaCrt(String caCrtPath) {
        if (caCrtPath == null || caCrtPath.isBlank()) {
            throw new IllegalArgumentException("MQTT_CA_CRT_PATH is required when TLS is enabled");
        }
        try (FileInputStream fis = new FileInputStream(caCrtPath)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate ca = cf.generateCertificate(fis);

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setCertificateEntry("ca", ca);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), new SecureRandom());
            return ctx.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build TLS socket factory", e);
        }
    }
}
