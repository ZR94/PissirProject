package upo.pissir.toll.service;

import org.junit.jupiter.api.Test;
import upo.pissir.toll.camera.CameraClient;
import upo.pissir.toll.config.MqttConfig;
import upo.pissir.toll.json.Json;
import upo.pissir.toll.mqtt.MqttGateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TollServiceTest {

    @Test
    void tollPriceReplyTopicUsesProvidedChannel() {
        TollService service = new TollService(null, null, "MI_Ovest", TollService.DeviceMode.EXIT_MANUAL);

        assertEquals("highway/MI_Ovest/exit/manual/responses", service.tollPriceReplyTopic("manual"));
        assertEquals("highway/MI_Ovest/exit/telepass/responses", service.tollPriceReplyTopic("telepass"));
    }

    @Test
    void entryManualIgnoresExitCommandByMode() {
        FakeGateway mqtt = new FakeGateway();
        FakeCamera camera = new FakeCamera(mqtt, "MI_Ovest");
        TollService service = new TollService(mqtt, camera, "MI_Ovest", TollService.DeviceMode.ENTRY_MANUAL);

        service.onCommand(
                "highway/MI_Ovest/exit/manual/commands",
                """
                        {"type":"EXIT_MANUAL_COMMAND","ticketId":"TCK-1"}
                        """
        );

        assertTrue(mqtt.published.isEmpty());
    }

    @Test
    void entryManualPublishesEntryAcceptedAfterCameraResponse() {
        FakeGateway mqtt = new FakeGateway();
        FakeCamera camera = new FakeCamera(mqtt, "MI_Ovest");
        TollService service = new TollService(mqtt, camera, "MI_Ovest", TollService.DeviceMode.ENTRY_MANUAL);

        service.onCommand(
                "highway/MI_Ovest/entry/manual/commands",
                """
                        {"type":"ENTRY_MANUAL_COMMAND","plate":"AA111AA"}
                        """
        );

        assertNotNull(camera.pendingPassId);
        assertTrue(camera.pendingPassId.startsWith("TCK-"));

        service.onCameraResponse(
                "highway/MI_Ovest/entry/manual/responses",
                """
                        {"type":"CAMERA_RESPONSE","correlationId":"c1","plate":"AA111AA"}
                        """
        );

        Published evt = mqtt.firstByTopic("highway/MI_Ovest/entry/manual/events");
        assertNotNull(evt);

        Map<String, Object> body = Json.parseToMap(evt.payload());
        assertEquals("ENTRY_ACCEPTED", Json.getString(body, "type"));
        assertEquals("AA111AA", Json.getString(body, "plate"));
        assertTrue(Json.getString(body, "ticketId").startsWith("TCK-"));
    }

    @Test
    void exitManualFlowPublishesPaymentRequestAndExitCompleted() {
        FakeGateway mqtt = new FakeGateway();
        TollService service = new TollService(mqtt, null, "MI_Ovest", TollService.DeviceMode.EXIT_MANUAL);

        service.onCommand(
                "highway/MI_Ovest/exit/manual/commands",
                """
                        {"type":"EXIT_MANUAL_COMMAND","ticketId":"TCK-1"}
                        """
        );

        Published reqMsg = mqtt.firstByTopic("highway/requests/tollprice");
        assertNotNull(reqMsg);
        String corrId = Json.getString(Json.parseToMap(reqMsg.payload()), "correlationId");
        assertNotNull(corrId);

        service.onTollPriceResponse(
                "highway/MI_Ovest/exit/manual/responses",
                """
                        {
                          "type":"TOLLPRICE_RESPONSE",
                          "correlationId":"%s",
                          "entryTollboothId":"VC_Est",
                          "plate":"AB123CD",
                          "entryAt":"2026-02-28T10:00:00Z",
                          "amountCents":720,
                          "currency":"EUR"
                        }
                        """.formatted(corrId)
        );

        Published state = mqtt.lastByTopic("highway/MI_Ovest/exit/manual/state");
        assertNotNull(state);
        Map<String, Object> stateBody = Json.parseToMap(state.payload());
        assertEquals("REQUEST_PAYMENT", Json.getString(stateBody, "type"));
        assertEquals("VC_Est", Json.getString(stateBody, "entryTollboothId"));
        assertEquals("AB123CD", Json.getString(stateBody, "plate"));

        service.onCommand(
                "highway/MI_Ovest/exit/manual/commands",
                """
                        {"type":"INSERT_PAYMENT","ticketId":"TCK-1","amountCents":0}
                        """
        );
        assertFalse(mqtt.anyByTopic("highway/MI_Ovest/exit/manual/events"));

        service.onCommand(
                "highway/MI_Ovest/exit/manual/commands",
                """
                        {"type":"INSERT_PAYMENT","ticketId":"TCK-1","amountCents":720}
                        """
        );
        assertTrue(mqtt.anyByTopic("highway/MI_Ovest/exit/manual/events"));
    }

    @Test
    void exitTelepassPublishesExitCompletedOnlyAfterPriceAndCamera() {
        FakeGateway mqtt = new FakeGateway();
        FakeCamera camera = new FakeCamera(mqtt, "MI_Ovest");
        TollService service = new TollService(mqtt, camera, "MI_Ovest", TollService.DeviceMode.EXIT_TELEPASS);

        service.onCommand(
                "highway/MI_Ovest/exit/telepass/commands",
                """
                        {"type":"EXIT_TELEPASS_COMMAND","telepassId":"TP-001"}
                        """
        );

        Published reqMsg = mqtt.firstByTopic("highway/requests/tollprice");
        assertNotNull(reqMsg);
        String corrId = Json.getString(Json.parseToMap(reqMsg.payload()), "correlationId");
        assertNotNull(corrId);

        service.onTollPriceResponse(
                "highway/MI_Ovest/exit/telepass/responses",
                """
                        {
                          "type":"TOLLPRICE_RESPONSE",
                          "correlationId":"%s",
                          "entryTollboothId":"VC_Est",
                          "amountCents":720,
                          "currency":"EUR"
                        }
                        """.formatted(corrId)
        );
        assertFalse(mqtt.anyByTopic("highway/MI_Ovest/exit/telepass/events"));

        service.onCameraResponse(
                "highway/MI_Ovest/exit/telepass/responses",
                """
                        {"type":"CAMERA_RESPONSE","correlationId":"c2","plate":"ZZ999YY"}
                        """
        );

        Published evt = mqtt.lastByTopic("highway/MI_Ovest/exit/telepass/events");
        assertNotNull(evt);
        Map<String, Object> body = Json.parseToMap(evt.payload());
        assertEquals("EXIT_COMPLETED", Json.getString(body, "type"));
        assertEquals("TP-001", Json.getString(body, "telepassId"));
        assertEquals("VC_Est", Json.getString(body, "entryTollboothId"));
        assertEquals("ZZ999YY", Json.getString(body, "plate"));
    }

    private record Published(String topic, String payload, int qos) {}

    private static final class FakeGateway extends MqttGateway {
        private final List<Published> published = new ArrayList<>();

        private FakeGateway() {
            super(new MqttConfig("localhost", 1883, false, "test", "test", ""), "test");
        }

        @Override
        public void publish(String topic, String payloadJson, int qos) {
            published.add(new Published(topic, payloadJson, qos));
        }

        private Published firstByTopic(String topic) {
            return published.stream().filter(p -> topic.equals(p.topic())).findFirst().orElse(null);
        }

        private Published lastByTopic(String topic) {
            Published last = null;
            for (Published p : published) {
                if (topic.equals(p.topic())) {
                    last = p;
                }
            }
            return last;
        }

        private boolean anyByTopic(String topic) {
            return published.stream().anyMatch(p -> topic.equals(p.topic()));
        }
    }

    private static final class FakeCamera extends CameraClient {
        private String pendingPassId;

        private FakeCamera(MqttGateway mqtt, String tollId) {
            super(mqtt, tollId);
        }

        @Override
        public void requestPlate(String direction, String channel, String passId) {
            pendingPassId = passId;
        }

        @Override
        public String consumePendingPassIdIfAny(Map<String, Object> cameraRespBody) {
            String out = pendingPassId;
            pendingPassId = null;
            return out;
        }
    }
}
