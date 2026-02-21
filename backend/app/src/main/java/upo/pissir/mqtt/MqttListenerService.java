package upo.pissir.mqtt;

import org.eclipse.paho.client.mqttv3.*;
import upo.pissir.json.Json;
import upo.pissir.service.TollProcessingService;
import javax.net.ssl.SSLSocketFactory;
import java.nio.charset.StandardCharsets;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MqttListenerService {

    private final MqttConfig cfg;
    private final TollProcessingService processingService;

    private MqttClient client;

    public MqttListenerService(MqttConfig cfg, TollProcessingService processingService) {
        this.cfg = cfg;
        this.processingService = processingService;
    }

    public void start() {
        try {
            String protocol = cfg.tlsEnabled() ? "ssl" : "tcp";
            String brokerUri = protocol + "://" + cfg.host() + ":" + cfg.port();

            String clientId = "server-" + UUID.randomUUID();
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

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("MQTT connection lost: " + (cause == null ? "" : cause.getMessage()));
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    handleMessage(topic, payload);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });

            client.connect(opt);

            client.subscribe(MqttTopics.ENTRY_EVENTS, 1);
            client.subscribe(MqttTopics.EXIT_EVENTS, 1);
            client.subscribe(MqttTopics.TOLLPRICE_REQUEST, 1);

            System.out.println("MQTT connected: " + brokerUri);
            System.out.println("Subscribed: " + MqttTopics.ENTRY_EVENTS + ", " + MqttTopics.EXIT_EVENTS + ", "
                    + MqttTopics.TOLLPRICE_REQUEST);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start MQTT listener", e);
        }
    }

    public void stop() throws Exception {
        if (client != null) {
            if (client.isConnected())
                client.disconnect();
            client.close();
        }
    }

    private void handleMessage(String topic, String payloadJson) {
        TopicParser.Parsed parsed = TopicParser.parse(topic);
        Map<String, Object> body = Json.parseToMap(payloadJson);
        String type = Json.getString(body, "type");

        if (type == null || type.isBlank()) {
            System.out.println("Ignoring message without type. Topic=" + topic);
            return;
        }

        // Global request
        if ("requests".equals(parsed.channel()) && "tollprice".equals(parsed.leaf())) {
            handleTollPriceRequest(body);
            return;
        }

        // Entry/Exit events
        if (!"events".equals(parsed.leaf()))
            return;

        switch (type) {
            case "ENTRY_ACCEPTED" -> processingService.onEntryAccepted(parsed.tollboothId(), parsed.channel(), body);
            case "EXIT_COMPLETED" -> processingService.onExitCompleted(parsed.tollboothId(), parsed.channel(), body);

            default -> System.out.println("Unknown event type=" + type + " topic=" + topic);
        }
    }

    private void handleTollPriceRequest(Map<String, Object> body) {
        String correlationId = Json.getString(body, "correlationId");
        String replyTopic = Json.getString(body, "replyTopic");

        if (correlationId == null || replyTopic == null) {
            System.out.println("Invalid TOLLPRICE_REQUEST: missing correlationId/replyTopic");
            return;
        }

        int amount = processingService.computeTollPrice(body);

        Map<String, Object> resp = new HashMap<>();
        resp.put("timestamp", Instant.now().toString());
        resp.put("type", "TOLLPRICE_RESPONSE");
        resp.put("correlationId", correlationId);
        resp.put("amountCents", amount);
        resp.put("currency", "EUR");

        publish(replyTopic, Json.toJson(resp), 1);
    }

    private void publish(String topic, String jsonPayload, int qos) {
        try {
            if (client == null || !client.isConnected()) {
                System.out.println("Cannot publish, MQTT not connected");
                return;
            }
            MqttMessage msg = new MqttMessage(jsonPayload.getBytes(StandardCharsets.UTF_8));
            msg.setQos(qos);
            client.publish(topic, msg);
        } catch (Exception e) {
            System.out.println("Publish failed: " + e.getMessage());
        }
    }
}
