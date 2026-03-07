package upo.pissir.service;

import org.junit.jupiter.api.Test;
import upo.pissir.repo.FareRepository;
import upo.pissir.repo.TelepassDebtRepository;
import upo.pissir.repo.TollboothRepository;
import upo.pissir.repo.TripRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TollProcessingServiceTest {

    @Test
    void resolveTollPriceUsesEntryFromRequestWhenProvided() {
        TollProcessingService service = new TollProcessingService(
                new FakeFareRepository(720),
                new FakeTripRepository(),
                new FakeDebtRepo(),
                new FakeTollboothRepository()
        );
        Map<String, Object> req = new HashMap<>();
        req.put("entryTollboothId", "VC_Est");
        req.put("exitTollboothId", "MI_Ovest");

        TollProcessingService.TollPriceResolved out = service.resolveTollPrice(req);

        assertEquals("VC_Est", out.entryTollboothId());
        assertEquals("MI_Ovest", out.exitTollboothId());
        assertEquals(720, out.amountCents());
        assertNull(out.plate());
        assertNull(out.entryAt());
    }

    @Test
    void resolveTollPriceCanResolveFromTicketId() {
        FakeTripRepository tripRepo = new FakeTripRepository();
        tripRepo.ticketRow = new TripRepository.ActiveTripRow(
                11L, "VC_Est", "AB123CD", Instant.parse("2026-02-28T10:00:00Z"));

        TollProcessingService service = new TollProcessingService(
                new FakeFareRepository(720),
                tripRepo,
                new FakeDebtRepo(),
                new FakeTollboothRepository()
        );
        Map<String, Object> req = new HashMap<>();
        req.put("ticketId", "TCK-1");
        req.put("exitTollboothId", "MI_Ovest");

        TollProcessingService.TollPriceResolved out = service.resolveTollPrice(req);

        assertEquals("VC_Est", out.entryTollboothId());
        assertEquals("AB123CD", out.plate());
        assertEquals("2026-02-28T10:00:00Z", out.entryAt().toString());
        assertEquals(720, out.amountCents());
    }

    @Test
    void resolveTollPriceCanResolveFromTelepassId() {
        FakeTripRepository tripRepo = new FakeTripRepository();
        tripRepo.telepassRow = new TripRepository.ActiveTripRow(
                21L, "MI_Est", "ZZ999YY", Instant.parse("2026-02-28T09:30:00Z"));

        TollProcessingService service = new TollProcessingService(
                new FakeFareRepository(450),
                tripRepo,
                new FakeDebtRepo(),
                new FakeTollboothRepository()
        );
        Map<String, Object> req = new HashMap<>();
        req.put("telepassId", "TP-1");
        req.put("exitTollboothId", "VC_Est");

        TollProcessingService.TollPriceResolved out = service.resolveTollPrice(req);

        assertEquals("MI_Est", out.entryTollboothId());
        assertEquals("ZZ999YY", out.plate());
        assertEquals(450, out.amountCents());
    }

    @Test
    void resolveTollPriceReturnsZeroWhenTripOrFareMissing() {
        TollProcessingService service = new TollProcessingService(
                new FakeFareRepository(null),
                new FakeTripRepository(),
                new FakeDebtRepo(),
                new FakeTollboothRepository()
        );
        Map<String, Object> req = new HashMap<>();
        req.put("ticketId", "NOT_FOUND");
        req.put("exitTollboothId", "MI_Ovest");

        TollProcessingService.TollPriceResolved out = service.resolveTollPrice(req);

        assertNull(out.entryTollboothId());
        assertEquals(0, out.amountCents());
        assertEquals("EUR", out.currency());
    }

    @Test
    void onExitCompletedComputesAverageSpeedWhenRoadMatches() {
        FakeTripRepository tripRepo = new FakeTripRepository();
        tripRepo.tripId = 99L;
        tripRepo.ticketRow = new TripRepository.ActiveTripRow(
                99L,
                "VC_Est",
                "AB123CD",
                Instant.parse("2026-03-07T10:00:00Z")
        );
        FakeTollboothRepository tollboothRepo = new FakeTollboothRepository();
        tollboothRepo.entry = new TollboothRepository.TollboothRow("VC_Est", "A4", 53.4, "Piemonte", "entry");
        tollboothRepo.exit = new TollboothRepository.TollboothRow("MI_Ovest", "A4", 126.1, "Lombardia", "exit");

        TollProcessingService service = new TollProcessingService(
                new FakeFareRepository(720),
                tripRepo,
                new FakeDebtRepo(),
                tollboothRepo
        );

        Map<String, Object> body = new HashMap<>();
        body.put("entryTollboothId", "VC_Est");
        body.put("ticketId", "TCK-1");
        body.put("amountCents", 720);
        body.put("timestamp", "2026-03-07T10:40:00Z");

        service.onExitCompleted("MI_Ovest", "manual", body);

        assertEquals(99L, tripRepo.closedTripId);
        assertEquals(109.05, tripRepo.lastAvgSpeedKmh);
        assertEquals(false, tripRepo.lastSpeeding);
    }

    @Test
    void onExitCompletedDoesNotComputeAverageSpeedWhenRoadDoesNotMatch() {
        FakeTripRepository tripRepo = new FakeTripRepository();
        tripRepo.tripId = 100L;
        tripRepo.ticketRow = new TripRepository.ActiveTripRow(
                100L,
                "VC_Est",
                "AB123CD",
                Instant.parse("2026-03-07T10:00:00Z")
        );
        FakeTollboothRepository tollboothRepo = new FakeTollboothRepository();
        tollboothRepo.entry = new TollboothRepository.TollboothRow("VC_Est", "A4", 53.4, "Piemonte", "entry");
        tollboothRepo.exit = new TollboothRepository.TollboothRow("AT_Est", "A21", 30.2, "Piemonte", "exit");

        TollProcessingService service = new TollProcessingService(
                new FakeFareRepository(720),
                tripRepo,
                new FakeDebtRepo(),
                tollboothRepo
        );

        Map<String, Object> body = new HashMap<>();
        body.put("entryTollboothId", "VC_Est");
        body.put("ticketId", "TCK-2");
        body.put("amountCents", 720);
        body.put("timestamp", "2026-03-07T10:40:00Z");

        service.onExitCompleted("AT_Est", "manual", body);

        assertEquals(100L, tripRepo.closedTripId);
        assertNull(tripRepo.lastAvgSpeedKmh);
        assertEquals(false, tripRepo.lastSpeeding);
    }

    private static final class FakeFareRepository extends FareRepository {
        private final Integer fare;

        private FakeFareRepository(Integer fare) {
            super(null);
            this.fare = fare;
        }

        @Override
        public Integer findFareCents(String entryTollboothId, String exitTollboothId) {
            return fare;
        }
    }

    private static final class FakeTripRepository extends TripRepository {
        private ActiveTripRow ticketRow;
        private ActiveTripRow telepassRow;
        private Long tripId;
        private long closedTripId = -1L;
        private Double lastAvgSpeedKmh;
        private boolean lastSpeeding;

        private FakeTripRepository() {
            super(null);
        }

        @Override
        public Long findActiveTripIdByTicket(String ticketId) {
            return tripId;
        }

        @Override
        public void closeTrip(long tripId, String exitTollboothId, Instant exitAt, int amountCents, Double avgSpeedKmh, boolean speeding, boolean paid) {
            this.closedTripId = tripId;
            this.lastAvgSpeedKmh = avgSpeedKmh;
            this.lastSpeeding = speeding;
        }

        @Override
        public ActiveTripRow findActiveTripByTicket(String ticketId) {
            return ticketRow;
        }

        @Override
        public ActiveTripRow findActiveTripByTelepass(String telepassId) {
            return telepassRow;
        }
    }

    private static final class FakeDebtRepo extends TelepassDebtRepository {
        private FakeDebtRepo() {
            super(null);
        }
    }

    private static final class FakeTollboothRepository extends TollboothRepository {
        private TollboothRow entry;
        private TollboothRow exit;

        private FakeTollboothRepository() {
            super(null);
        }

        @Override
        public TollboothRow findById(String id) {
            if (entry != null && entry.id().equals(id)) return entry;
            if (exit != null && exit.id().equals(id)) return exit;
            return null;
        }
    }
}
