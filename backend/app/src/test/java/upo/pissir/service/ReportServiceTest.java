package upo.pissir.service;

import org.junit.jupiter.api.Test;
import upo.pissir.dto.RouteStatsResponse;
import upo.pissir.dto.TripResponse;
import upo.pissir.repo.TripRepository;
import upo.pissir.dto.ActiveTripResponse;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportServiceTest {

    @Test
    void defaultLimitIs100WhenMissing() {
        FakeTripRepository tripRepo = new FakeTripRepository();
        ReportService service = new ReportService(tripRepo);

        service.listTrips(null, null, null, null, null, null, null);

        assertEquals(100, tripRepo.lastLimit);
    }

    @Test
    void invalidLimitThrows() {
        ReportService service = new ReportService(new FakeTripRepository());

        assertThrows(IllegalArgumentException.class,
                () -> service.listTrips(null, null, null, null, null, null, "0"));
        assertThrows(IllegalArgumentException.class,
                () -> service.listTrips(null, null, null, null, null, null, "501"));
    }

    @Test
    void invalidChannelThrows() {
        ReportService service = new ReportService(new FakeTripRepository());

        assertThrows(IllegalArgumentException.class,
                () -> service.listTrips(null, null, null, null, "foo", null, null));
    }

    @Test
    void fromAfterToThrows() {
        ReportService service = new ReportService(new FakeTripRepository());

        assertThrows(IllegalArgumentException.class,
                () -> service.listTrips(
                        "2026-03-01T10:00:00Z",
                        "2026-03-01T09:00:00Z",
                        null,
                        null,
                        null,
                        null,
                        null
                ));
    }

    @Test
    void routeStatsMappingIsCorrect() {
        FakeTripRepository tripRepo = new FakeTripRepository();
        tripRepo.routeRows = List.of(
                new TripRepository.RouteStatsRow("VC_Est", "MI_Ovest", 10, 7, 3, 7200, 720)
        );
        ReportService service = new ReportService(tripRepo);

        List<RouteStatsResponse> out = service.listRouteStats(
                "2026-03-01T00:00:00Z",
                "2026-03-02T00:00:00Z",
                null,
                null,
                "manual",
                "true"
        );

        assertEquals(1, out.size());
        RouteStatsResponse row = out.get(0);
        assertEquals("VC_Est", row.entryTollboothId());
        assertEquals("MI_Ovest", row.exitTollboothId());
        assertEquals(10, row.tripsCount());
        assertEquals(7, row.paidTripsCount());
        assertEquals(3, row.unpaidTripsCount());
        assertEquals(7200, row.totalAmountCents());
        assertEquals(720, row.avgAmountCents());
        assertEquals("EUR", row.currency());
        assertEquals("manual", tripRepo.lastChannel);
        assertEquals(true, tripRepo.lastPaid);
        assertEquals(Instant.parse("2026-03-01T00:00:00Z"), tripRepo.lastFrom);
        assertEquals(Instant.parse("2026-03-02T00:00:00Z"), tripRepo.lastTo);
    }

    @Test
    void activeTripsDefaultLimitIs100WhenMissing() {
        FakeTripRepository tripRepo = new FakeTripRepository();
        ReportService service = new ReportService(tripRepo);

        service.listActiveTrips(null, null, null);

        assertEquals(100, tripRepo.lastActiveLimit);
    }

    @Test
    void activeTripsInvalidChannelThrows() {
        ReportService service = new ReportService(new FakeTripRepository());

        assertThrows(IllegalArgumentException.class,
                () -> service.listActiveTrips(null, "foo", null));
    }

    @Test
    void activeTripsMappingIsCorrect() {
        FakeTripRepository tripRepo = new FakeTripRepository();
        tripRepo.activeRows = List.of(
                new TripRepository.ActiveTripReportRow(
                        12L,
                        "VC_Est",
                        "TCK-001",
                        null,
                        "AB123CD",
                        Instant.now().minusSeconds(180)
                )
        );
        ReportService service = new ReportService(tripRepo);

        List<ActiveTripResponse> out = service.listActiveTrips("VC_Est", "manual", "50");

        assertEquals(1, out.size());
        ActiveTripResponse row = out.get(0);
        assertEquals(12L, row.id());
        assertEquals("VC_Est", row.entryTollboothId());
        assertEquals("TCK-001", row.passId());
        assertEquals("manual", row.channel());
        assertEquals("AB123CD", row.plate());
        assertEquals(50, tripRepo.lastActiveLimit);
        assertEquals("manual", tripRepo.lastActiveChannel);
    }

    private static final class FakeTripRepository extends TripRepository {
        private int lastLimit = -1;
        private int lastActiveLimit = -1;
        private Instant lastFrom;
        private Instant lastTo;
        private String lastChannel;
        private Boolean lastPaid;
        private String lastActiveChannel;
        private List<RouteStatsRow> routeRows = List.of();
        private List<ActiveTripReportRow> activeRows = List.of();

        private FakeTripRepository() {
            super(null);
        }

        @Override
        public List<TripResponse> findTripsForReport(
                Instant from,
                Instant to,
                String entryTollboothId,
                String exitTollboothId,
                String channel,
                Boolean paid,
                int limit
        ) {
            this.lastFrom = from;
            this.lastTo = to;
            this.lastChannel = channel;
            this.lastPaid = paid;
            this.lastLimit = limit;
            return List.of();
        }

        @Override
        public List<RouteStatsRow> findRouteStatsForReport(
                Instant from,
                Instant to,
                String entryTollboothId,
                String exitTollboothId,
                String channel,
                Boolean paid
        ) {
            this.lastFrom = from;
            this.lastTo = to;
            this.lastChannel = channel;
            this.lastPaid = paid;
            return routeRows;
        }

        @Override
        public List<ActiveTripReportRow> findActiveTrips(String entryTollboothId, String channel, int limit) {
            this.lastActiveChannel = channel;
            this.lastActiveLimit = limit;
            return activeRows;
        }
    }
}
