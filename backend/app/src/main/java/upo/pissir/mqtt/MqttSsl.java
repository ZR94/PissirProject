package upo.pissir.mqtt;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.SSLContext;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public final class MqttSsl {
    private MqttSsl() {}

    // Build a trust store in-memory from a PEM CA certificate (ca.crt)
    public static SSLSocketFactory socketFactoryFromCaCrt(String caCrtPath) {
        if (caCrtPath == null || caCrtPath.isBlank()) {
            throw new IllegalArgumentException("MQTT_CA_CRT is required when MQTT_TLS=true");
        }

        try (FileInputStream fis = new FileInputStream(caCrtPath)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate caCert = (X509Certificate) cf.generateCertificate(fis);

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setCertificateEntry("ca", caCert);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);

            return ctx.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException("Failed creating TLS socket factory", e);
        }
    }
}
