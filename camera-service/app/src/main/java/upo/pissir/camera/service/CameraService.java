package upo.pissir.camera.service;

import upo.pissir.camera.json.Json;
import upo.pissir.camera.mqtt.MqttGateway;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class CameraService {

    private final MqttGateway mqtt;
    private final Random rnd = new Random();

    public CameraService(MqttGateway mqtt) {
        this.mqtt = mqtt;
    }

    public void onCameraRequest(String topic, String payloadJson) {
        String[] p = topic.split("/");
        if (p.length < 5 || !"highway".equals(p[0])) return;

        String tollboothId = p[1];
        String direction = p[2];
        if (!"camera".equals(p[3]) || !"requests".equals(p[4])) return;
        if (!"entry".equals(direction) && !"exit".equals(direction)) return;

        Map<String, Object> body = Json.parseToMap(payloadJson);
        String type = Json.getString(body, "type");
        if (!"CAMERA_REQUEST".equals(type) && !"CAMERA_PLATE_REQUEST".equals(type)) return;

        String correlationId = Json.getString(body, "correlationId");
        if (correlationId == null || correlationId.isBlank()) return;

        String passId = Json.getString(body, "passId");
        String channel = Json.getString(body, "channel");
        if (channel == null || channel.isBlank()) {
            channel = (passId != null && passId.startsWith("TCK-")) ? "manual" : "telepass";
        }

        if (!"manual".equals(channel) && !"telepass".equals(channel)) return;

        Map<String, Object> resp = new HashMap<>();
        resp.put("timestamp", Instant.now().toString());
        resp.put("type", "CAMERA_RESPONSE");
        resp.put("correlationId", correlationId);
        resp.put("plate", randomPlate());
        resp.put("confidence", randomConfidence());

        // legacy fields di compatibilità
        resp.put("direction", direction);
        if (passId != null) resp.put("passId", passId);

        String responseTopic = "highway/" + tollboothId + "/" + direction + "/" + channel + "/responses";
        mqtt.publish(responseTopic, Json.toJson(resp), 1);
    }

    private String randomPlate() {
        char a = (char) ('A' + rnd.nextInt(26));
        char b = (char) ('A' + rnd.nextInt(26));
        int n = 100 + rnd.nextInt(900);
        char c = (char) ('A' + rnd.nextInt(26));
        char d = (char) ('A' + rnd.nextInt(26));
        return "" + a + b + n + c + d;
    }

    private double randomConfidence() {
        return 0.90 + (rnd.nextDouble() * 0.09);
    }
}
