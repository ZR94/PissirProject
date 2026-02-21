package upo.pissir.toll.camera;

import upo.pissir.toll.json.Json;
import upo.pissir.toll.mqtt.MqttGateway;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CameraClient {

    private final MqttGateway mqtt;
    private final String tollId;

    private final Map<String, String> pending = new ConcurrentHashMap<>();

    public CameraClient(MqttGateway mqtt, String tollId) {
        this.mqtt = mqtt;
        this.tollId = tollId;
    }

    public void requestPlate(String direction, String channel, String passId) {
        String correlationId = UUID.randomUUID().toString();
        pending.put(correlationId, passId);

        Map<String, Object> req = new HashMap<>();
        req.put("timestamp", Instant.now().toString());
        req.put("type", "CAMERA_REQUEST");
        req.put("correlationId", correlationId);
        req.put("direction", direction);
        req.put("channel", channel);
        req.put("passId", passId);

        String topic = "highway/" + tollId + "/" + direction + "/camera/requests";
        mqtt.publish(topic, Json.toJson(req), 1);
    }

    public String consumePendingPassIdIfAny(Map<String, Object> cameraRespBody) {
        String correlationId = Json.getString(cameraRespBody, "correlationId");
        return correlationId == null ? null : pending.remove(correlationId);
    }
}

