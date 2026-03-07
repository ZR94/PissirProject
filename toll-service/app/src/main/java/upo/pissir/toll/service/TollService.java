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

    public enum DeviceMode {
        ENTRY_MANUAL("entry", "manual"),
        ENTRY_TELEPASS("entry", "telepass"),
        EXIT_MANUAL("exit", "manual"),
        EXIT_TELEPASS("exit", "telepass");

        private final String direction;
        private final String channel;

        DeviceMode(String direction, String channel) {
            this.direction = direction;
            this.channel = channel;
        }

        public String direction() {
            return direction;
        }

        public String channel() {
            return channel;
        }

        public boolean isEntry() {
            return "entry".equals(direction);
        }

        public boolean isExit() {
            return "exit".equals(direction);
        }
    }

    private record PendingTollPrice(String channel, String passId) {}
    private record PendingManualPayment(String entryTollboothId, int amountCents, String currency) {}
    private record PendingExitTelepass(String entryTollboothId, Integer amountCents, String currency, String plate) {}

    private final MqttGateway mqtt;
    private final CameraClient camera;
    private final String tollId;
    private final DeviceMode mode;

    private final Map<String, PendingTollPrice> pendingTollPrice = new ConcurrentHashMap<>();
    private final Map<String, PendingManualPayment> pendingManualPayment = new ConcurrentHashMap<>();
    private final Map<String, PendingExitTelepass> pendingExitTelepass = new ConcurrentHashMap<>();

    public TollService(MqttGateway mqtt, CameraClient camera, String tollId, DeviceMode mode) {
        this.mqtt = mqtt;
        this.camera = camera;
        this.tollId = tollId;
        this.mode = mode;
    }

    public String tollPriceReplyTopic(String channel) {
        return "highway/" + tollId + "/exit/" + channel + "/responses";
    }

    public String serviceReplyTopic() {
        return "highway/" + tollId + "/" + mode.direction() + "/" + mode.channel() + "/service";
    }

    public void onCommand(String topic, String payloadJson) {
        TopicParser.Parsed p = TopicParser.parse(topic);
        if (!"commands".equals(p.leaf())) return;
        if (!tollId.equals(p.tollboothId())) return;
        if (!mode.direction().equals(p.direction())) return;
        if (!mode.channel().equals(p.channel())) return;

        Map<String, Object> body = Json.parseToMap(payloadJson);
        String type = Json.getString(body, "type");
        if (type == null) return;

        if (mode.isEntry()) {
            handleEntryCommand(body, type);
            return;
        }
        handleExitCommand(body, type);
    }

    public void onTollPriceResponse(String topic, String payloadJson) {
        if (!mode.isExit()) return;

        TopicParser.Parsed p = TopicParser.parse(topic);
        if (!"responses".equals(p.leaf())) return;
        if (!"exit".equals(p.direction())) return;
        if (!tollId.equals(p.tollboothId())) return;
        if (!mode.channel().equals(p.channel())) return;

        Map<String, Object> body = Json.parseToMap(payloadJson);
        String type = Json.getString(body, "type");
        if (!"TOLLPRICE_RESPONSE".equals(type)) return;

        String correlationId = Json.getString(body, "correlationId");
        Integer amountCents = Json.getInt(body, "amountCents");
        if (correlationId == null || amountCents == null) return;

        PendingTollPrice pending = pendingTollPrice.remove(correlationId);
        if (pending == null) return;

        String entryTollboothId = Json.getString(body, "entryTollboothId");
        String plate = Json.getString(body, "plate");
        String entryAt = Json.getString(body, "entryAt");
        String currency = Json.getString(body, "currency");
        if (currency == null || currency.isBlank()) {
            currency = "EUR";
        }

        if ("manual".equals(mode.channel())) {
            if (entryTollboothId == null || entryTollboothId.isBlank() || amountCents <= 0) {
                publishFault("FARE_NOT_FOUND", "No fare found for manual exit request", "WARN");
                publishState("exit", "manual", Map.of(
                        "timestamp", Instant.now().toString(),
                        "type", "EXIT_REJECTED",
                        "reason", "FARE_NOT_FOUND",
                        "ticketId", pending.passId()
                ));
                return;
            }
            pendingManualPayment.put(
                    pending.passId(),
                    new PendingManualPayment(entryTollboothId, amountCents, currency)
            );
            publishExitManualPaymentRequested(pending.passId(), amountCents, currency, entryTollboothId, plate, entryAt);
            return;
        }

        if (entryTollboothId == null || entryTollboothId.isBlank() || amountCents <= 0) {
            publishFault("FARE_NOT_FOUND", "No fare found for telepass exit request", "WARN");
            publishState("exit", "telepass", Map.of(
                    "timestamp", Instant.now().toString(),
                    "type", "EXIT_REJECTED",
                    "reason", "FARE_NOT_FOUND",
                    "telepassId", pending.passId()
            ));
            pendingExitTelepass.remove(pending.passId());
            return;
        }

        PendingExitTelepass current = pendingExitTelepass.get(pending.passId());
        if (current == null) {
            current = new PendingExitTelepass(entryTollboothId, amountCents, currency, null);
        } else {
            current = new PendingExitTelepass(entryTollboothId, amountCents, currency, current.plate());
        }
        pendingExitTelepass.put(pending.passId(), current);
        publishExitTelepassIfReady(pending.passId());
    }

    public void onServiceResponse(String topic, String payloadJson) {
        TopicParser.Parsed p = TopicParser.parse(topic);
        if (!"service".equals(p.leaf())) return;
        if (!tollId.equals(p.tollboothId())) return;
        if (!mode.direction().equals(p.direction())) return;
        if (!mode.channel().equals(p.channel())) return;

        Map<String, Object> body = Json.parseToMap(payloadJson);
        String type = Json.getString(body, "type");
        if (!"SERVICE_RESPONSE".equals(type)) return;

        String action = Json.getString(body, "action");
        String message = Json.getString(body, "message");
        String faultId = Json.getString(body, "faultId");
        System.out.println("Service response received for " + tollId + "/" + mode.direction() + "/" + mode.channel()
                + " faultId=" + faultId + " action=" + action + " message=" + message);

        Map<String, Object> state = new HashMap<>();
        state.put("timestamp", Instant.now().toString());
        state.put("type", "SERVICE_RESPONSE_RECEIVED");
        if (faultId != null) {
            state.put("faultId", faultId);
        }
        if (action != null) {
            state.put("action", action);
        }
        if (message != null && !message.isBlank()) {
            state.put("message", message);
        }
        publishState(mode.direction(), mode.channel(), state);
    }

    public void onCameraResponse(String topic, String payloadJson) {
        if (camera == null) return;

        TopicParser.Parsed p = TopicParser.parse(topic);
        if (!"responses".equals(p.leaf())) return;
        if (!tollId.equals(p.tollboothId())) return;
        if (!mode.channel().equals(p.channel())) return;

        Map<String, Object> body = Json.parseToMap(payloadJson);
        String type = Json.getString(body, "type");
        if (!"CAMERA_RESPONSE".equals(type) && !"CAMERA_PLATE_RESPONSE".equals(type)) return;

        if (mode.isEntry()) {
            handleEntryCameraResponse(p, body);
            return;
        }
        if (mode == DeviceMode.EXIT_TELEPASS) {
            handleExitTelepassCameraResponse(p, body);
        }
    }

    private void handleEntryCommand(Map<String, Object> cmd, String type) {
        if (camera == null) {
            publishFault("CAMERA_UNAVAILABLE", "Entry device cannot read plate because camera client is not configured", "ERROR");
            publishState("entry", mode.channel(), Map.of(
                    "timestamp", Instant.now().toString(),
                    "type", "ENTRY_REJECTED",
                    "reason", "CAMERA_UNAVAILABLE"
            ));
            return;
        }

        if ("manual".equals(mode.channel())) {
            if (!Commands.ENTRY_MANUAL_COMMAND.equals(type) && !Commands.REQUEST_ENTRY.equals(type)) return;
            String ticketId = "TCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            camera.requestPlate("entry", "manual", ticketId);
            publishState("entry", "manual", Map.of(
                    "timestamp", Instant.now().toString(),
                    "type", "ENTRY_PENDING",
                    "ticketId", ticketId
            ));
            return;
        }

        if (!Commands.ENTRY_TELEPASS_COMMAND.equals(type) && !Commands.REQUEST_ENTRY.equals(type)) return;
        String telepassId = Json.getString(cmd, "telepassId");
        if (telepassId == null || telepassId.isBlank()) return;

        camera.requestPlate("entry", "telepass", telepassId);
        publishState("entry", "telepass", Map.of(
                "timestamp", Instant.now().toString(),
                "type", "ENTRY_PENDING",
                "telepassId", telepassId
        ));
    }

    private void handleExitCommand(Map<String, Object> cmd, String type) {
        if ("manual".equals(mode.channel())) {
            if (Commands.INSERT_PAYMENT.equals(type)) {
                handleManualPaymentInsert(cmd);
                return;
            }
            if (!Commands.EXIT_MANUAL_COMMAND.equals(type) && !Commands.REQUEST_EXIT.equals(type)) return;

            String ticketId = Json.getString(cmd, "ticketId");
            if (ticketId == null) ticketId = Json.getString(cmd, "passId");
            if (ticketId == null || ticketId.isBlank()) return;

            requestTollPrice("manual", ticketId, Json.getString(cmd, "entryTollboothId"));
            publishState("exit", "manual", Map.of(
                    "timestamp", Instant.now().toString(),
                    "type", "EXIT_PENDING_PRICE",
                    "ticketId", ticketId
            ));
            return;
        }

        if (!Commands.EXIT_TELEPASS_COMMAND.equals(type) && !Commands.REQUEST_EXIT.equals(type)) return;

        String telepassId = Json.getString(cmd, "telepassId");
        if (telepassId == null) telepassId = Json.getString(cmd, "passId");
        if (telepassId == null || telepassId.isBlank()) return;

        if (camera == null) {
            publishFault("CAMERA_UNAVAILABLE", "Exit telepass device cannot read plate because camera client is not configured", "ERROR");
            publishState("exit", "telepass", Map.of(
                    "timestamp", Instant.now().toString(),
                    "type", "EXIT_REJECTED",
                    "reason", "CAMERA_UNAVAILABLE",
                    "telepassId", telepassId
            ));
            return;
        }

        camera.requestPlate("exit", "telepass", telepassId);
        requestTollPrice("telepass", telepassId, Json.getString(cmd, "entryTollboothId"));
        publishState("exit", "telepass", Map.of(
                "timestamp", Instant.now().toString(),
                "type", "EXIT_PENDING_VALIDATION",
                "telepassId", telepassId
        ));
    }

    private void handleManualPaymentInsert(Map<String, Object> cmd) {
        String ticketId = Json.getString(cmd, "ticketId");
        if (ticketId == null) ticketId = Json.getString(cmd, "passId");
        Integer insertedAmount = Json.getInt(cmd, "amountCents");
        if (ticketId == null || insertedAmount == null || insertedAmount <= 0) return;

        PendingManualPayment pending = pendingManualPayment.get(ticketId);
        if (pending == null) return;

        if (insertedAmount < pending.amountCents()) {
            publishState("exit", "manual", Map.of(
                    "timestamp", Instant.now().toString(),
                    "type", "PAYMENT_REJECTED",
                    "reason", "INSUFFICIENT_AMOUNT",
                    "ticketId", ticketId,
                    "amountCentsDue", pending.amountCents()
            ));
            return;
        }

        pendingManualPayment.remove(ticketId);
        publishExitCompletedManual(pending.entryTollboothId(), ticketId, pending.amountCents());
        publishState("exit", "manual", Map.of(
                "timestamp", Instant.now().toString(),
                "type", "PAYMENT_ACCEPTED",
                "ticketId", ticketId,
                "amountCents", pending.amountCents(),
                "currency", pending.currency()
        ));
    }

    private void requestTollPrice(String channel, String passId, String entryTollboothIdFromCommand) {
        String correlationId = UUID.randomUUID().toString();
        pendingTollPrice.put(correlationId, new PendingTollPrice(channel, passId));

        Map<String, Object> req = new HashMap<>();
        req.put("timestamp", Instant.now().toString());
        req.put("type", "TOLLPRICE_REQUEST");
        req.put("correlationId", correlationId);
        req.put("replyTopic", tollPriceReplyTopic(channel));
        req.put("exitTollboothId", tollId);

        if (entryTollboothIdFromCommand != null && !entryTollboothIdFromCommand.isBlank()) {
            req.put("entryTollboothId", entryTollboothIdFromCommand);
        }
        if ("manual".equals(channel)) {
            req.put("ticketId", passId);
        } else {
            req.put("telepassId", passId);
        }

        mqtt.publish("highway/requests/tollprice", Json.toJson(req), 1);
    }

    private void publishEntryAccepted(String channel, String plate, String passId) {
        Map<String, Object> evt = new HashMap<>();
        evt.put("timestamp", Instant.now().toString());
        evt.put("type", "ENTRY_ACCEPTED");
        evt.put("plate", plate);

        if ("manual".equals(channel)) {
            evt.put("ticketId", passId);
        } else {
            evt.put("telepassId", passId);
        }

        mqtt.publish("highway/" + tollId + "/entry/" + channel + "/events", Json.toJson(evt), 1);
    }

    private void publishExitCompletedManual(String entryTollboothId, String ticketId, int amountCents) {
        Map<String, Object> evt = new HashMap<>();
        evt.put("timestamp", Instant.now().toString());
        evt.put("type", "EXIT_COMPLETED");
        evt.put("entryTollboothId", entryTollboothId);
        evt.put("amountCents", amountCents);
        evt.put("ticketId", ticketId);

        mqtt.publish("highway/" + tollId + "/exit/manual/events", Json.toJson(evt), 1);
    }

    private void publishExitCompletedTelepass(String entryTollboothId, String telepassId, int amountCents, String plate) {
        Map<String, Object> evt = new HashMap<>();
        evt.put("timestamp", Instant.now().toString());
        evt.put("type", "EXIT_COMPLETED");
        evt.put("entryTollboothId", entryTollboothId);
        evt.put("amountCents", amountCents);
        evt.put("telepassId", telepassId);
        if (plate != null && !plate.isBlank()) {
            evt.put("plate", plate);
        }

        mqtt.publish("highway/" + tollId + "/exit/telepass/events", Json.toJson(evt), 1);
    }

    private void publishExitManualPaymentRequested(
            String ticketId,
            int amountCentsDue,
            String currency,
            String entryTollboothId,
            String plate,
            String entryAt
    ) {
        Map<String, Object> state = new HashMap<>();
        state.put("timestamp", Instant.now().toString());
        state.put("type", Commands.REQUEST_PAYMENT);
        state.put("ticketId", ticketId);
        state.put("amountCents", amountCentsDue);
        state.put("currency", currency);
        state.put("entryTollboothId", entryTollboothId);
        if (plate != null && !plate.isBlank()) {
            state.put("plate", plate);
        }
        if (entryAt != null && !entryAt.isBlank()) {
            state.put("entryAt", entryAt);
        }

        mqtt.publish("highway/" + tollId + "/exit/manual/state", Json.toJson(state), 1);
    }

    private void publishState(String direction, String channel, Map<String, Object> state) {
        mqtt.publish("highway/" + tollId + "/" + direction + "/" + channel + "/state", Json.toJson(state), 1);
    }

    private void publishFault(String code, String message, String severity) {
        Map<String, Object> fault = new HashMap<>();
        fault.put("timestamp", Instant.now().toString());
        fault.put("type", "DEVICE_FAULT");
        fault.put("code", code);
        fault.put("message", message);
        fault.put("severity", severity);
        mqtt.publish("highway/" + tollId + "/" + mode.direction() + "/" + mode.channel() + "/faults", Json.toJson(fault), 1);
    }

    private void handleEntryCameraResponse(TopicParser.Parsed p, Map<String, Object> body) {
        if (!"entry".equals(p.direction())) return;

        String passId = camera.consumePendingPassIdIfAny(body);
        if (passId == null) {
            passId = Json.getString(body, "passId");
        }
        String plate = Json.getString(body, "plate");
        if (passId == null || passId.isBlank() || plate == null || plate.isBlank()) return;

        publishEntryAccepted(mode.channel(), plate, passId);

        publishState("entry", mode.channel(), Map.of(
                "timestamp", Instant.now().toString(),
                "type", "ENTRY_ACCEPTED_UI",
                "passId", passId,
                "plate", plate
        ));
    }

    private void handleExitTelepassCameraResponse(TopicParser.Parsed p, Map<String, Object> body) {
        if (!"exit".equals(p.direction())) return;

        String telepassId = camera.consumePendingPassIdIfAny(body);
        if (telepassId == null) {
            telepassId = Json.getString(body, "passId");
        }
        if (telepassId == null) {
            telepassId = Json.getString(body, "telepassId");
        }
        String plate = Json.getString(body, "plate");
        if (telepassId == null || telepassId.isBlank() || plate == null || plate.isBlank()) return;

        PendingExitTelepass current = pendingExitTelepass.get(telepassId);
        if (current == null) {
            current = new PendingExitTelepass(null, null, "EUR", plate);
        } else {
            current = new PendingExitTelepass(
                    current.entryTollboothId(),
                    current.amountCents(),
                    current.currency(),
                    plate
            );
        }
        pendingExitTelepass.put(telepassId, current);
        publishExitTelepassIfReady(telepassId);
    }

    private void publishExitTelepassIfReady(String telepassId) {
        PendingExitTelepass pending = pendingExitTelepass.get(telepassId);
        if (pending == null) return;
        if (pending.entryTollboothId() == null || pending.entryTollboothId().isBlank()) return;
        if (pending.amountCents() == null || pending.amountCents() <= 0) return;
        if (pending.plate() == null || pending.plate().isBlank()) return;

        pendingExitTelepass.remove(telepassId);
        publishExitCompletedTelepass(
                pending.entryTollboothId(),
                telepassId,
                pending.amountCents(),
                pending.plate()
        );

        publishState("exit", "telepass", Map.of(
                "timestamp", Instant.now().toString(),
                "type", "EXIT_COMPLETED_UI",
                "telepassId", telepassId,
                "amountCents", pending.amountCents(),
                "currency", pending.currency(),
                "plate", pending.plate()
        ));
    }
}
