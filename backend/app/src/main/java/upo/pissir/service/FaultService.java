package upo.pissir.service;

import upo.pissir.dto.FaultReplyRequest;
import upo.pissir.dto.FaultResponse;
import upo.pissir.json.Json;
import upo.pissir.mqtt.MqttPublisher;
import upo.pissir.repo.FaultRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FaultService {
    private static final Set<String> ALLOWED_SEVERITIES = Set.of("WARN", "ERROR");
    private static final Set<String> ALLOWED_ACTIONS = Set.of("ACK", "TECHNICIAN_DISPATCHED", "RESET_REQUESTED");

    private final FaultRepository faultRepo;
    private final MqttPublisher mqttPublisher;

    public FaultService(FaultRepository faultRepo, MqttPublisher mqttPublisher) {
        this.faultRepo = faultRepo;
        this.mqttPublisher = mqttPublisher;
    }

    public List<FaultResponse> listFaults() {
        return faultRepo.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public long recordFault(String tollboothId, String direction, String channel, Map<String, Object> body) {
        String code = normalizeString(body.get("code"));
        String message = normalizeString(body.get("message"));
        String severity = normalizeUpper(body.get("severity"));
        Instant createdAt = parseInstant(normalizeString(body.get("timestamp")));

        if (tollboothId == null || tollboothId.isBlank()) {
            throw new IllegalArgumentException("fault tollboothId is required");
        }
        if (!"entry".equals(direction) && !"exit".equals(direction)) {
            throw new IllegalArgumentException("fault direction must be entry or exit");
        }
        if (!"manual".equals(channel) && !"telepass".equals(channel) && !"camera".equals(channel)) {
            throw new IllegalArgumentException("fault channel must be manual, telepass or camera");
        }
        if (code == null || message == null) {
            throw new IllegalArgumentException("fault code and message are required");
        }
        if (!ALLOWED_SEVERITIES.contains(severity)) {
            throw new IllegalArgumentException("fault severity must be WARN or ERROR");
        }
        return faultRepo.createFault(tollboothId, direction, channel, code, message, severity, createdAt);
    }

    public FaultResponse respondToFault(long faultId, FaultReplyRequest req) {
        if (faultId <= 0) {
            throw new IllegalArgumentException("faultId must be positive");
        }
        if (req == null) {
            throw new IllegalArgumentException("request is required");
        }
        String action = normalizeUpper(req.action());
        String message = normalizeString(req.message());
        if (!ALLOWED_ACTIONS.contains(action)) {
            throw new IllegalArgumentException("action must be ACK, TECHNICIAN_DISPATCHED or RESET_REQUESTED");
        }
        if (message == null) {
            throw new IllegalArgumentException("message is required");
        }

        FaultRepository.FaultRow fault = faultRepo.findById(faultId);
        if (fault == null) {
            throw new IllegalStateException("fault not found");
        }
        if (!"OPEN".equals(fault.status())) {
            throw new IllegalStateException("fault already responded");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("type", "SERVICE_RESPONSE");
        payload.put("faultId", faultId);
        payload.put("action", action);
        payload.put("message", message);

        mqttPublisher.publish(serviceTopic(fault), Json.toJson(payload), 1);

        if (!faultRepo.markResponded(faultId, action, Instant.now())) {
            throw new IllegalStateException("fault already responded");
        }
        FaultRepository.FaultRow updated = faultRepo.findById(faultId);
        return toResponse(updated);
    }

    private static String serviceTopic(FaultRepository.FaultRow fault) {
        return "highway/" + fault.tollboothId() + "/" + fault.direction() + "/" + fault.channel() + "/service";
    }

    private FaultResponse toResponse(FaultRepository.FaultRow row) {
        return new FaultResponse(
                row.id(),
                row.tollboothId(),
                row.direction(),
                row.channel(),
                row.code(),
                row.message(),
                row.severity(),
                row.status(),
                row.backendAction(),
                row.createdAt() == null ? null : row.createdAt().toString(),
                row.respondedAt() == null ? null : row.respondedAt().toString()
        );
    }

    private static String normalizeString(Object raw) {
        if (raw == null) return null;
        String value = String.valueOf(raw).trim();
        return value.isBlank() ? null : value;
    }

    private static String normalizeUpper(Object raw) {
        String value = normalizeString(raw);
        return value == null ? null : value.toUpperCase();
    }

    private static Instant parseInstant(String raw) {
        if (raw == null) return Instant.now();
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
