package upo.pissir.camera;

import upo.pissir.camera.config.MqttConfig;
import upo.pissir.camera.mqtt.MqttGateway;
import upo.pissir.camera.service.CameraService;

public class Main {

    public static void main(String[] args) {
        MqttConfig cfg = MqttConfig.fromEnv();
        MqttGateway mqtt = new MqttGateway(cfg, "camera");

        CameraService service = new CameraService(mqtt);

        mqtt.connect();

        // Listen for plate requests from any toll/direction
        mqtt.subscribe("highway/+/+/camera/requests", 1, service::onCameraRequest);

        System.out.println("CameraService started.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                mqtt.close();
            } catch (Exception ignored) {}
        }));

        try {
            Thread.currentThread().join();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
