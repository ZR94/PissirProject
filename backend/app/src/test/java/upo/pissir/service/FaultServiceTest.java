package upo.pissir.service;

import org.junit.jupiter.api.Test;
import upo.pissir.dto.FaultReplyRequest;
import upo.pissir.dto.FaultResponse;
import upo.pissir.mqtt.MqttPublisher;
import upo.pissir.repo.FaultRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FaultServiceTest {

    @Test
    void recordFaultRejectsInvalidSeverity() {
        FaultService service = new FaultService(new FakeFaultRepository(), new FakeMqttPublisher());

        assertThrows(IllegalArgumentException.class, () -> service.recordFault(
                "MI_Ovest",
                "exit",
                "telepass",
                Map.of("code", "CAMERA_UNAVAILABLE", "message", "camera down", "severity", "INFO")
        ));
    }

    @Test
    void respondPublishesMessageAndMarksFault() {
        FakeFaultRepository repo = new FakeFaultRepository();
        repo.row = new FaultRepository.FaultRow(
                7L, "MI_Ovest", "exit", "telepass", "CAMERA_UNAVAILABLE", "camera down",
                "ERROR", "OPEN", null, Instant.parse("2026-03-07T10:00:00Z"), null
        );
        FakeMqttPublisher publisher = new FakeMqttPublisher();
        FaultService service = new FaultService(repo, publisher);

        FaultResponse out = service.respondToFault(7L, new FaultReplyRequest("ACK", "Technician notified"));

        assertEquals("highway/MI_Ovest/exit/telepass/service", publisher.lastTopic);
        assertEquals(7L, repo.lastRespondedId);
        assertEquals("ACK", repo.lastBackendAction);
        assertEquals("RESPONDED", out.status());
        assertEquals("ACK", out.backendAction());
    }

    @Test
    void respondRejectsAlreadyRespondedFault() {
        FakeFaultRepository repo = new FakeFaultRepository();
        repo.row = new FaultRepository.FaultRow(
                8L, "MI_Ovest", "exit", "telepass", "CAMERA_UNAVAILABLE", "camera down",
                "ERROR", "RESPONDED", "ACK", Instant.parse("2026-03-07T10:00:00Z"), Instant.parse("2026-03-07T10:05:00Z")
        );
        FaultService service = new FaultService(repo, new FakeMqttPublisher());

        assertThrows(IllegalStateException.class,
                () -> service.respondToFault(8L, new FaultReplyRequest("ACK", "Already handled")));
    }

    private static final class FakeFaultRepository extends FaultRepository {
        private FaultRow row;
        private long lastRespondedId = -1L;
        private String lastBackendAction;

        private FakeFaultRepository() {
            super(null);
        }

        @Override
        public List<FaultRow> findAll() {
            return row == null ? List.of() : List.of(row);
        }

        @Override
        public FaultRow findById(long id) {
            return row != null && row.id() == id ? row : null;
        }

        @Override
        public long createFault(String tollboothId, String direction, String channel, String code, String message, String severity, Instant createdAt) {
            this.row = new FaultRow(11L, tollboothId, direction, channel, code, message, severity, "OPEN", null, createdAt, null);
            return 11L;
        }

        @Override
        public boolean markResponded(long id, String backendAction, Instant respondedAt) {
            if (row == null || row.id() != id || !"OPEN".equals(row.status())) {
                return false;
            }
            lastRespondedId = id;
            lastBackendAction = backendAction;
            row = new FaultRow(row.id(), row.tollboothId(), row.direction(), row.channel(), row.code(), row.message(),
                    row.severity(), "RESPONDED", backendAction, row.createdAt(), respondedAt);
            return true;
        }
    }

    private static final class FakeMqttPublisher extends MqttPublisher {
        private String lastTopic;

        private FakeMqttPublisher() {
            super(null);
        }

        @Override
        public void publish(String topic, String jsonPayload, int qos) {
            lastTopic = topic;
        }
    }
}
