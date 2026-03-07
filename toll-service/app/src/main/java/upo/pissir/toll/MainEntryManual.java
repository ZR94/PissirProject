package upo.pissir.toll;

import upo.pissir.toll.camera.CameraClient;
import upo.pissir.toll.config.MqttConfig;
import upo.pissir.toll.mqtt.MqttGateway;
import upo.pissir.toll.service.TollService;

public class MainEntryManual {

    public static void main(String[] args) {
        String tollId = env("TOLL_ID", "MI_Ovest");
        MqttConfig cfg = MqttConfig.fromEnv();
        MqttGateway mqtt = new MqttGateway(cfg, "toll-entry-manual-" + tollId);
        CameraClient camera = new CameraClient(mqtt, tollId);
        TollService service = new TollService(mqtt, camera, tollId, TollService.DeviceMode.ENTRY_MANUAL);

        mqtt.connect();
        mqtt.subscribe("highway/+/entry/manual/commands", 1, service::onCommand);
        mqtt.subscribe("highway/" + tollId + "/entry/manual/responses", 1, service::onCameraResponse);
        mqtt.subscribe(service.serviceReplyTopic(), 1, service::onServiceResponse);

        System.out.println("EntryManual device started. tollId=" + tollId);
        keepAlive(mqtt);
    }

    private static void keepAlive(MqttGateway mqtt) {
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
