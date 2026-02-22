package upo.pissir.toll;

import upo.pissir.toll.camera.CameraClient;
import upo.pissir.toll.config.MqttConfig;
import upo.pissir.toll.mqtt.MqttGateway;
import upo.pissir.toll.service.SessionStore;
import upo.pissir.toll.service.TollService;

public class Main {

    public static void main(String[] args) {
        String tollId = env("TOLL_ID", "MI_Ovest");

        MqttConfig cfg = MqttConfig.fromEnv();
        MqttGateway mqtt = new MqttGateway(cfg, "toll-" + tollId);

        SessionStore store = new SessionStore();
        CameraClient cameraClient = new CameraClient(mqtt, tollId);
        TollService service = new TollService(mqtt, cameraClient, store, tollId);

        mqtt.connect();

        // Commands coming from UI (or tests)
        mqtt.subscribe("highway/+/entry/+/commands", 1, service::onCommand);
        mqtt.subscribe("highway/+/exit/+/commands", 1, service::onCommand);

        // Responses for toll price requests (replyTopic is fixed by this service)
        mqtt.subscribe(service.tollPriceReplyTopic("manual"), 1, service::onTollPriceResponse);
        mqtt.subscribe(service.tollPriceReplyTopic("telepass"), 1, service::onTollPriceResponse);

        // Camera responses (replyTopic is fixed by this service)
        mqtt.subscribe("highway/" + tollId + "/entry/manual/responses", 1, service::onCameraResponse);
        mqtt.subscribe("highway/" + tollId + "/entry/telepass/responses", 1, service::onCameraResponse);

        System.out.println("TollService started. tollId=" + tollId);

        // Keep alive
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                mqtt.close();
            } catch (Exception ignored) {
            }
        }));

        try {
            Thread.currentThread().join();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
