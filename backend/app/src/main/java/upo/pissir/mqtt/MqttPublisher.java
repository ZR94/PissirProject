package upo.pissir.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.net.ssl.SSLSocketFactory;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MqttPublisher {
    private final MqttConfig cfg;
    private MqttClient client;

    public MqttPublisher(MqttConfig cfg) {
        this.cfg = cfg;
    }

    public void start() {
        try {
            String protocol = cfg.tlsEnabled() ? "ssl" : "tcp";
            String brokerUri = protocol + "://" + cfg.host() + ":" + cfg.port();
            client = new MqttClient(brokerUri, "server-publisher-" + UUID.randomUUID(), new MemoryPersistence());

            MqttConnectOptions opt = new MqttConnectOptions();
            opt.setAutomaticReconnect(true);
            opt.setCleanSession(true);
            opt.setUserName(cfg.username());
            opt.setPassword(cfg.password().toCharArray());
            opt.setConnectionTimeout(10);
            opt.setKeepAliveInterval(20);

            if (cfg.tlsEnabled()) {
                SSLSocketFactory sf = MqttSsl.socketFactoryFromCaCrt(cfg.caCrtPath());
                opt.setSocketFactory(sf);
            }

            client.connect(opt);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start MQTT publisher", e);
        }
    }

    public void publish(String topic, String jsonPayload, int qos) {
        try {
            if (client == null || !client.isConnected()) {
                throw new IllegalStateException("MQTT publisher not connected");
            }
            MqttMessage msg = new MqttMessage(jsonPayload.getBytes(StandardCharsets.UTF_8));
            msg.setQos(qos);
            client.publish(topic, msg);
        } catch (Exception e) {
            throw new IllegalStateException("MQTT publish failed: " + topic, e);
        }
    }

    public void stop() throws Exception {
        if (client != null) {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        }
    }
}
