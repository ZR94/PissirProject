package upo.pissir.mqtt;

import org.eclipse.paho.client.mqttv3.*;
import upo.pissir.json.Json;
import upo.pissir.service.FaultService;
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
    private final FaultService faultService;
    private final MqttPublisher publisher;

    private MqttClient client;

    public MqttListenerService(
            MqttConfig cfg,
            TollProcessingService processingService,
            FaultService faultService,
            MqttPublisher publisher
    ) {
        this.cfg = cfg;
        this.processingService = processingService;
        this.faultService = faultService;
        this.publisher = publisher;
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
            client.subscribe(MqttTopics.FAULTS, 1);

            System.out.println("MQTT connected: " + brokerUri);
            System.out.println("Subscribed: " + MqttTopics.ENTRY_EVENTS + ", " + MqttTopics.EXIT_EVENTS + ", "
                    + MqttTopics.TOLLPRICE_REQUEST + ", " + MqttTopics.FAULTS);
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

        if ("faults".equals(parsed.leaf())) {
            handleFault(parsed, body);
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

    private void handleFault(TopicParser.Parsed parsed, Map<String, Object> body) {
        String type = Json.getString(body, "type");
        if (!"DEVICE_FAULT".equals(type)) {
            return;
        }
        try {
            long id = faultService.recordFault(parsed.tollboothId(), parsed.direction(), parsed.channel(), body);
            System.out.println("Recorded device fault id=" + id + " tollbooth=" + parsed.tollboothId());
        } catch (Exception e) {
            System.out.println("Ignoring invalid DEVICE_FAULT: " + e.getMessage());
        }
    }

    private void handleTollPriceRequest(Map<String, Object> body) {
        String correlationId = Json.getString(body, "correlationId");
        String replyTopic = Json.getString(body, "replyTopic");

        if (correlationId == null || replyTopic == null) {
            System.out.println("Invalid TOLLPRICE_REQUEST: missing correlationId/replyTopic");
            return;
        }

        TollProcessingService.TollPriceResolved resolved = processingService.resolveTollPrice(body);

        Map<String, Object> resp = new HashMap<>();
        resp.put("timestamp", Instant.now().toString());
        resp.put("type", "TOLLPRICE_RESPONSE");
        resp.put("correlationId", correlationId);
        resp.put("amountCents", resolved.amountCents());
        resp.put("currency", resolved.currency());
        if (resolved.entryTollboothId() != null) {
            resp.put("entryTollboothId", resolved.entryTollboothId());
        }
        if (resolved.plate() != null) {
            resp.put("plate", resolved.plate());
        }
        if (resolved.entryAt() != null) {
            resp.put("entryAt", resolved.entryAt().toString());
        }

        publisher.publish(replyTopic, Json.toJson(resp), 1);
    }
}
