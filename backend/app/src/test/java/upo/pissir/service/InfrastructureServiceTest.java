package upo.pissir.service;

import org.junit.jupiter.api.Test;
import upo.pissir.dto.CreateTollboothRequest;
import upo.pissir.dto.CreateDeviceRequest;
import upo.pissir.dto.UpdateTollboothRequest;
import upo.pissir.repo.DeviceRepository;
import upo.pissir.repo.FareRepository;
import upo.pissir.repo.TollboothRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfrastructureServiceTest {

    @Test
    void updateFareWithValidAmountCallsRepository() {
        FakeFareRepository fareRepo = new FakeFareRepository();
        InfrastructureService service = new InfrastructureService(new FakeTollboothRepository(), fareRepo);

        service.updateFare(11L, 850);

        assertEquals(11L, fareRepo.lastUpdatedId);
        assertEquals(850, fareRepo.lastUpdatedAmount);
    }

    @Test
    void updateFareRejectsNegativeAmount() {
        InfrastructureService service = new InfrastructureService(new FakeTollboothRepository(), new FakeFareRepository());

        assertThrows(IllegalArgumentException.class, () -> service.updateFare(1L, -1));
    }

    @Test
    void updateFareThrowsWhenFareNotFound() {
        FakeFareRepository fareRepo = new FakeFareRepository();
        fareRepo.updateResult = false;
        InfrastructureService service = new InfrastructureService(new FakeTollboothRepository(), fareRepo);

        assertThrows(IllegalStateException.class, () -> service.updateFare(999L, 100));
    }

    @Test
    void deleteFareThrowsWhenFareNotFound() {
        FakeFareRepository fareRepo = new FakeFareRepository();
        fareRepo.deleteResult = false;
        InfrastructureService service = new InfrastructureService(new FakeTollboothRepository(), fareRepo);

        assertThrows(IllegalStateException.class, () -> service.deleteFare(999L));
    }

    @Test
    void deleteFareWithValidIdCallsRepository() {
        FakeFareRepository fareRepo = new FakeFareRepository();
        InfrastructureService service = new InfrastructureService(new FakeTollboothRepository(), fareRepo);

        service.deleteFare(5L);

        assertEquals(5L, fareRepo.lastDeletedId);
        assertTrue(fareRepo.deleteCalled);
    }

    @Test
    void createTollboothCreatesMandatoryDevicesWhenConfigured() {
        FakeDeviceRepository deviceRepo = new FakeDeviceRepository();
        InfrastructureService service = new InfrastructureService(new FakeTollboothRepository(false), new FakeFareRepository(), deviceRepo);

        service.createTollbooth(new CreateTollboothRequest("NEW_TB", "A4", 10.5, "Piemonte", "Nuovo casello"));

        assertEquals("NEW_TB", deviceRepo.lastMandatoryTollboothId);
    }

    @Test
    void updateTollboothRejectsNegativeKmMarker() {
        InfrastructureService service = new InfrastructureService(new FakeTollboothRepository(true), new FakeFareRepository(), new FakeDeviceRepository());

        assertThrows(IllegalArgumentException.class, () ->
                service.updateTollbooth("MI_Ovest", new UpdateTollboothRequest("A4", -1.0, "Lombardia", "desc")));
    }

    @Test
    void updateTollboothWithValidPayloadCallsRepository() {
        FakeTollboothRepository tollboothRepo = new FakeTollboothRepository(true);
        InfrastructureService service = new InfrastructureService(tollboothRepo, new FakeFareRepository(), new FakeDeviceRepository());

        service.updateTollbooth("MI_Ovest", new UpdateTollboothRequest("A4", 126.4, "Lombardia", "Milano Ovest"));

        assertEquals("MI_Ovest", tollboothRepo.lastUpdatedId);
        assertEquals("A4", tollboothRepo.lastUpdatedRoadCode);
        assertEquals(126.4, tollboothRepo.lastUpdatedKmMarker);
        assertEquals("Lombardia", tollboothRepo.lastUpdatedRegion);
        assertEquals("Milano Ovest", tollboothRepo.lastUpdatedDescription);
    }

    @Test
    void createDeviceRejectsInvalidDirection() {
        InfrastructureService service = new InfrastructureService(
                new FakeTollboothRepository(true),
                new FakeFareRepository(),
                new FakeDeviceRepository()
        );

        assertThrows(IllegalArgumentException.class, () ->
                service.createDevice(new CreateDeviceRequest("MI_Ovest", "north", "manual", true)));
    }

    @Test
    void setDeviceEnabledThrowsWhenMissing() {
        FakeDeviceRepository deviceRepo = new FakeDeviceRepository();
        deviceRepo.updateResult = false;
        InfrastructureService service = new InfrastructureService(new FakeTollboothRepository(true), new FakeFareRepository(), deviceRepo);

        assertThrows(IllegalStateException.class, () -> service.setDeviceEnabled(42L, true));
    }

    private static final class FakeTollboothRepository extends TollboothRepository {
        private final boolean existsResult;
        private String lastUpdatedId;
        private String lastUpdatedRoadCode;
        private Double lastUpdatedKmMarker;
        private String lastUpdatedRegion;
        private String lastUpdatedDescription;

        private FakeTollboothRepository() {
            this(true);
        }

        private FakeTollboothRepository(boolean existsResult) {
            super(null);
            this.existsResult = existsResult;
        }

        @Override
        public void create(String id, String roadCode, double kmMarker, String region, String description) {
            // no-op for unit test
        }

        @Override
        public boolean update(String id, String roadCode, double kmMarker, String region, String description) {
            lastUpdatedId = id;
            lastUpdatedRoadCode = roadCode;
            lastUpdatedKmMarker = kmMarker;
            lastUpdatedRegion = region;
            lastUpdatedDescription = description;
            return existsResult;
        }

        @Override
        public boolean exists(String id) {
            return existsResult;
        }
    }

    private static final class FakeFareRepository extends FareRepository {
        private boolean updateResult = true;
        private boolean deleteResult = true;
        private long lastUpdatedId = -1;
        private int lastUpdatedAmount = -1;
        private long lastDeletedId = -1;
        private boolean deleteCalled = false;

        private FakeFareRepository() {
            super(null);
        }

        @Override
        public boolean updateFare(long id, int amountCents) {
            lastUpdatedId = id;
            lastUpdatedAmount = amountCents;
            return updateResult;
        }

        @Override
        public boolean deleteFare(long id) {
            deleteCalled = true;
            lastDeletedId = id;
            return deleteResult;
        }
    }

    private static final class FakeDeviceRepository extends DeviceRepository {
        private String lastMandatoryTollboothId;
        private boolean updateResult = true;

        private FakeDeviceRepository() {
            super(null);
        }

        @Override
        public void createMandatoryForTollbooth(String tollboothId) {
            lastMandatoryTollboothId = tollboothId;
        }

        @Override
        public long create(String tollboothId, String direction, String channel, boolean enabled) {
            return 1L;
        }

        @Override
        public boolean updateEnabled(long id, boolean enabled) {
            return updateResult;
        }
    }
}
