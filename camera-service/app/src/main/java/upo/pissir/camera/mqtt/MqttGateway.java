package upo.pissir.camera.mqtt;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import upo.pissir.camera.config.MqttConfig;

import javax.net.ssl.SSLSocketFactory;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MqttGateway {

    public interface MessageHandler {
        void onMessage(String topic, String payload);
    }

    private final MqttConfig cfg;
    private final String clientIdPrefix;
    private MqttClient client;

    public MqttGateway(MqttConfig cfg, String clientIdPrefix) {
        this.cfg = cfg;
        this.clientIdPrefix = clientIdPrefix;
    }

    public void connect() {
        try {
            String protocol = cfg.tlsEnabled() ? "ssl" : "tcp";
            String brokerUri = protocol + "://" + cfg.host() + ":" + cfg.port();
            String clientId = clientIdPrefix + "-" + UUID.randomUUID();

            client = new MqttClient(brokerUri, clientId, new MemoryPersistence());

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
            System.out.println("MQTT connected: " + brokerUri);
        } catch (Exception e) {
            throw new IllegalStateException("MQTT connect failed", e);
        }
    }

    public void subscribe(String topic, int qos, MessageHandler handler) {
        try {
            client.subscribe(topic, qos, (t, msg) -> {
                String payload = new String(msg.getPayload(), StandardCharsets.UTF_8);
                handler.onMessage(t, payload);
            });
        } catch (Exception e) {
            throw new IllegalStateException("MQTT subscribe failed: " + topic, e);
        }
    }

    public void publish(String topic, String payloadJson, int qos) {
        try {
            MqttMessage msg = new MqttMessage(payloadJson.getBytes(StandardCharsets.UTF_8));
            msg.setQos(qos);
            client.publish(topic, msg);
        } catch (Exception e) {
            throw new IllegalStateException("MQTT publish failed: " + topic, e);
        }
    }

    public void close() throws Exception {
        if (client != null) {
            if (client.isConnected()) client.disconnect();
            client.close();
        }
    }
}
