package upo.pissir.toll.service;

import upo.pissir.toll.camera.CameraClient;
import upo.pissir.toll.json.Json;
import upo.pissir.toll.mqtt.MqttGateway;
import upo.pissir.toll.mqtt.TopicParser;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TollService {

    private final MqttGateway mqtt;
    private final CameraClient camera;
    private final SessionStore store;
    private final String tollId;

    private final Map<String, String> pendingTollPrice = new ConcurrentHashMap<>();

    public TollService(MqttGateway mqtt, CameraClient camera, SessionStore store, String tollId) {
        this.mqtt = mqtt;
        this.camera = camera;
        this.store = store;
        this.tollId = tollId;
    }

    public String tollPriceReplyTopic(String channel) {
        return "highway/" + tollId + "/exit/" + channel + "/responses";
    }

    public void onCommand(String topic, String payloadJson) {
        TopicParser.Parsed p = TopicParser.parse(topic);
        if (!"commands".equals(p.leaf())) return;
        if (!tollId.equals(p.tollboothId())) return;

        Map<String, Object> body = Json.parseToMap(payloadJson);
        String type = Json.getString(body, "type");
        if (type == null) return;

        if ("entry".equals(p.direction())) {
            handleEntryCommand(p.channel(), body, type);
            return;
        }

        if ("exit".equals(p.direction())) {
            handleExitCommand(p.channel(), body, type);
        }
    }

    public void onTollPriceResponse(String topic, String payloadJson) {
        Map<String, Object> body = Json.parseToMap(payloadJson);
        String type = Json.getString(body, "type");
        if (!"TOLLPRICE_RESPONSE".equals(type)) return;

        String correlationId = Json.getString(body, "correlationId");
        Integer amountCents = Json.getInt(body, "amountCents");
        if (correlationId == null || amountCents == null) return;

        String passId = pendingTollPrice.remove(correlationId);
        if (passId == null) return;

        SessionStore.Session s = store.get(passId);
        if (s == null) return;

        if ("telepass".equals(s.channel())) {
            publishExitCompletedTelepass(s, amountCents);
            store.remove(passId);
            return;
        }

        publishExitManualPaymentRequested(passId, amountCents);
    }

    public void onCameraResponse(String topic, String payloadJson) {
        TopicParser.Parsed p = TopicParser.parse(topic);
        if (!"responses".equals(p.leaf())) return;
        if (!"entry".equals(p.direction())) return;
        if (!tollId.equals(p.tollboothId())) return;

        Map<String, Object> body = Json.parseToMap(payloadJson);
        String type = Json.getString(body, "type");
        if (!"CAMERA_RESPONSE".equals(type) && !"CAMERA_PLATE_RESPONSE".equals(type)) return;

        String passId = camera.consumePendingPassIdIfAny(body);
        if (passId == null) {
            passId = Json.getString(body, "passId"); // fallback legacy
        }
        String plate = Json.getString(body, "plate");
        if (passId == null || plate == null || plate.isBlank()) return;

        String channel = p.channel();
        if (!"manual".equals(channel) && !"telepass".equals(channel)) {
            channel = passId.startsWith("TCK-") ? "manual" : "telepass";
        }

        store.put(new SessionStore.Session(passId, channel, tollId, plate, Instant.now()));
        publishEntryAccepted(channel, plate, passId);

        publishState("entry", channel, Map.of(
                "timestamp", Instant.now().toString(),
                "type", "ENTRY_ACCEPTED_UI",
                "passId", passId,
                "plate", plate
        ));
    }

    private void handleEntryCommand(String channel, Map<String, Object> cmd, String type) {
        boolean isManual = "manual".equals(channel);
        boolean isTelepass = "telepass".equals(channel);

        if (isManual && !Commands.ENTRY_MANUAL_COMMAND.equals(type) && !Commands.REQUEST_ENTRY.equals(type)) return;
        if (isTelepass && !Commands.ENTRY_TELEPASS_COMMAND.equals(type) && !Commands.REQUEST_ENTRY.equals(type)) return;
        if (!isManual && !isTelepass) return;

        String passId;
        if (isManual) {
            passId = "TCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } else {
            passId = Json.getString(cmd, "telepassId");
            if (passId == null || passId.isBlank()) return;
        }

        camera.requestPlate("entry", channel, passId);

        publishState("entry", channel, Map.of(
                "timestamp", Instant.now().toString(),
                "type", "ENTRY_PENDING",
                "passId", passId
        ));
    }

    private void handleExitCommand(String channel, Map<String, Object> cmd, String type) {
        boolean isManual = "manual".equals(channel);
        boolean isTelepass = "telepass".equals(channel);

        boolean isExitType = false;
        if (isManual && (Commands.EXIT_MANUAL_COMMAND.equals(type) || Commands.REQUEST_EXIT.equals(type))) {
            isExitType = true;
        }
        if (isTelepass && (Commands.EXIT_TELEPASS_COMMAND.equals(type) || Commands.REQUEST_EXIT.equals(type))) {
            isExitType = true;
        }

        if (isExitType) {
            String passId = extractPassIdForExit(channel, cmd);
            if (passId == null || passId.isBlank()) return;

            SessionStore.Session s = store.get(passId);
            if (s == null) {
                publishState("exit", channel, Map.of(
                        "timestamp", Instant.now().toString(),
                        "type", "EXIT_REJECTED",
                        "reason", "NO_ACTIVE_SESSION",
                        "passId", passId
                ));
                return;
            }

            requestTollPrice(s.entryTollboothId(), tollId, passId, channel);

            publishState("exit", channel, Map.of(
                    "timestamp", Instant.now().toString(),
                    "type", "EXIT_PENDING_PRICE",
                    "passId", passId
            ));
            return;
        }

        if (isManual && Commands.INSERT_PAYMENT.equals(type)) {
            String passId = Json.getString(cmd, "ticketId");
            if (passId == null) passId = Json.getString(cmd, "passId"); // fallback legacy
            Integer amountCents = Json.getInt(cmd, "amountCents");
            if (passId == null || amountCents == null) return;

            SessionStore.Session s = store.get(passId);
            if (s == null) return;

            publishExitCompletedManual(s, amountCents);
            store.remove(passId);

            publishState("exit", "manual", Map.of(
                    "timestamp", Instant.now().toString(),
                    "type", "PAYMENT_ACCEPTED",
                    "passId", passId,
                    "amountCents", amountCents
            ));
        }
    }

    private String extractPassIdForExit(String channel, Map<String, Object> cmd) {
        if ("manual".equals(channel)) {
            String ticketId = Json.getString(cmd, "ticketId");
            if (ticketId != null && !ticketId.isBlank()) return ticketId;
            return Json.getString(cmd, "passId"); // fallback legacy
        }

        if ("telepass".equals(channel)) {
            String telepassId = Json.getString(cmd, "telepassId");
            if (telepassId != null && !telepassId.isBlank()) return telepassId;
            return Json.getString(cmd, "passId"); // fallback legacy
        }

        return null;
    }

    private void requestTollPrice(String entryTollboothId, String exitTollboothId, String passId, String channel) {
        String correlationId = UUID.randomUUID().toString();
        pendingTollPrice.put(correlationId, passId);

        Map<String, Object> req = new HashMap<>();
        req.put("timestamp", Instant.now().toString());
        req.put("type", "TOLLPRICE_REQUEST");
        req.put("correlationId", correlationId);
        req.put("replyTopic", tollPriceReplyTopic(channel));
        req.put("entryTollboothId", entryTollboothId);
        req.put("exitTollboothId", exitTollboothId);

        if ("manual".equals(channel)) req.put("ticketId", passId);
        if ("telepass".equals(channel)) req.put("telepassId", passId);

        mqtt.publish("highway/requests/tollprice", Json.toJson(req), 1);
    }

    private void publishEntryAccepted(String channel, String plate, String passId) {
        Map<String, Object> evt = new HashMap<>();
        evt.put("timestamp", Instant.now().toString());
        evt.put("type", "ENTRY_ACCEPTED");
        evt.put("plate", plate);

        if ("manual".equals(channel)) evt.put("ticketId", passId);
        if ("telepass".equals(channel)) evt.put("telepassId", passId);

        String topic = "highway/" + tollId + "/entry/" + channel + "/events";
        mqtt.publish(topic, Json.toJson(evt), 1);
    }

    private void publishExitCompletedManual(SessionStore.Session s, int amountCents) {
        Map<String, Object> evt = new HashMap<>();
        evt.put("timestamp", Instant.now().toString());
        evt.put("type", "EXIT_COMPLETED");
        evt.put("entryTollboothId", s.entryTollboothId());
        evt.put("amountCents", amountCents);
        evt.put("ticketId", s.passId());

        mqtt.publish("highway/" + tollId + "/exit/manual/events", Json.toJson(evt), 1);
    }

    private void publishExitCompletedTelepass(SessionStore.Session s, int amountCents) {
        Map<String, Object> evt = new HashMap<>();
        evt.put("timestamp", Instant.now().toString());
        evt.put("type", "EXIT_COMPLETED");
        evt.put("entryTollboothId", s.entryTollboothId());
        evt.put("amountCents", amountCents);
        evt.put("telepassId", s.passId());

        mqtt.publish("highway/" + tollId + "/exit/telepass/events", Json.toJson(evt), 1);
    }

    private void publishExitManualPaymentRequested(String passId, int amountCentsDue) {
        Map<String, Object> state = new HashMap<>();
        state.put("timestamp", Instant.now().toString());
        state.put("type", Commands.REQUEST_PAYMENT);
        state.put("passId", passId);
        state.put("amountCents", amountCentsDue);

        mqtt.publish("highway/" + tollId + "/exit/manual/state", Json.toJson(state), 1);
    }

    private void publishState(String direction, String channel, Map<String, Object> state) {
        mqtt.publish("highway/" + tollId + "/" + direction + "/" + channel + "/state", Json.toJson(state), 1);
    }
}

