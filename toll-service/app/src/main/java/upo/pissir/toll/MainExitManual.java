package upo.pissir.toll;

import upo.pissir.toll.config.MqttConfig;
import upo.pissir.toll.mqtt.MqttGateway;
import upo.pissir.toll.service.TollService;

public class MainExitManual {

    public static void main(String[] args) {
        String tollId = env("TOLL_ID", "MI_Ovest");
        MqttConfig cfg = MqttConfig.fromEnv();
        MqttGateway mqtt = new MqttGateway(cfg, "toll-exit-manual-" + tollId);
        TollService service = new TollService(mqtt, null, tollId, TollService.DeviceMode.EXIT_MANUAL);

        mqtt.connect();
        mqtt.subscribe("highway/+/exit/manual/commands", 1, service::onCommand);
        mqtt.subscribe(service.tollPriceReplyTopic("manual"), 1, service::onTollPriceResponse);
        mqtt.subscribe(service.serviceReplyTopic(), 1, service::onServiceResponse);

        System.out.println("ExitManual device started. tollId=" + tollId);
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
