package upo.pissir.service;

import upo.pissir.json.Json;
import upo.pissir.repo.FareRepository;
import upo.pissir.repo.TelepassDebtRepository;
import upo.pissir.repo.TollboothRepository;
import upo.pissir.repo.TripRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public class TollProcessingService {

    private final FareRepository fareRepo;
    private final TripRepository tripRepo;
    private final TelepassDebtRepository debtRepo;
    private final TollboothRepository tollboothRepo;

    public record TollPriceResolved(
            String entryTollboothId,
            String exitTollboothId,
            String plate,
            Instant entryAt,
            int amountCents,
            String currency
    ) {}

    public TollProcessingService(
            FareRepository fareRepo,
            TripRepository tripRepo,
            TelepassDebtRepository debtRepo,
            TollboothRepository tollboothRepo
    ) {
        this.fareRepo = fareRepo;
        this.tripRepo = tripRepo;
        this.debtRepo = debtRepo;
        this.tollboothRepo = tollboothRepo;
    }

    public void onEntryAccepted(String entryTollboothId, String channel, Map<String, Object> body) {
        Instant ts = parseTimestamp(body);
        String plate = Json.getString(body, "plate");

        if ("manual".equals(channel)) {
            String ticketId = Json.getString(body, "ticketId");
            if (plate == null || ticketId == null) {
                System.out.println("Invalid ENTRY_ACCEPTED manual: missing plate/ticketId");
                return;
            }
            tripRepo.createTripManual(entryTollboothId, plate, ticketId, ts);
            return;
        }

        if ("telepass".equals(channel)) {
            String telepassId = Json.getString(body, "telepassId");
            if (plate == null || telepassId == null) {
                System.out.println("Invalid ENTRY_ACCEPTED telepass: missing plate/telepassId");
                return;
            }
            tripRepo.createTripTelepass(entryTollboothId, plate, telepassId, ts);
            return;
        }

        System.out.println("ENTRY_ACCEPTED ignored: channel=" + channel);
    }

    public void onExitCompleted(String exitTollboothId, String channel, Map<String, Object> body) {
        Instant ts = parseTimestamp(body);
        String entryTollboothId = Json.getString(body, "entryTollboothId");
        Integer amountCents = Json.getInt(body, "amountCents");

        if (entryTollboothId == null || amountCents == null) {
            System.out.println("Invalid EXIT_COMPLETED: missing entryTollboothId/amountCents");
            return;
        }

        if ("manual".equals(channel)) {
            String ticketId = Json.getString(body, "ticketId");
            if (ticketId == null) {
                System.out.println("Invalid EXIT_COMPLETED manual: missing ticketId");
                return;
            }
            Long tripId = tripRepo.findActiveTripIdByTicket(ticketId);
            if (tripId == null) {
                System.out.println("No active trip for ticketId=" + ticketId);
                return;
            }
            TripMetrics metrics = computeTripMetrics(entryTollboothId, exitTollboothId, body);
            tripRepo.closeTrip(tripId, exitTollboothId, ts, amountCents, metrics.avgSpeedKmh(), metrics.speeding(), true);
            return;
        }

        if ("telepass".equals(channel)) {
            String telepassId = Json.getString(body, "telepassId");
            if (telepassId == null) {
                System.out.println("Invalid EXIT_COMPLETED telepass: missing telepassId");
                return;
            }
            Long tripId = tripRepo.findActiveTripIdByTelepass(telepassId);
            if (tripId == null) {
                System.out.println("No active trip for telepassId=" + telepassId);
                return;
            }
            TripMetrics metrics = computeTripMetrics(entryTollboothId, exitTollboothId, body);
            tripRepo.closeTrip(tripId, exitTollboothId, ts, amountCents, metrics.avgSpeedKmh(), metrics.speeding(), false);
            debtRepo.createDebt(telepassId, tripId, amountCents, ts);
            return;
        }

        System.out.println("EXIT_COMPLETED ignored: channel=" + channel);
    }

    public int computeTollPrice(Map<String, Object> body) {
        String entry = Json.getString(body, "entryTollboothId");
        String exit = Json.getString(body, "exitTollboothId");
        if (entry == null || exit == null) return 0;

        Integer cents = fareRepo.findFareCents(entry, exit);
        return cents == null ? 0 : cents;
    }

    public TollPriceResolved resolveTollPrice(Map<String, Object> body) {
        String exit = Json.getString(body, "exitTollboothId");
        if (exit == null || exit.isBlank()) {
            return new TollPriceResolved(null, null, null, null, 0, "EUR");
        }

        String entry = Json.getString(body, "entryTollboothId");
        TripRepository.ActiveTripRow activeTrip = resolveActiveTrip(body);
        if ((entry == null || entry.isBlank()) && activeTrip != null) {
            entry = activeTrip.entryTollboothId();
        }

        int amount = 0;
        if (entry != null && !entry.isBlank()) {
            Integer cents = fareRepo.findFareCents(entry, exit);
            amount = cents == null ? 0 : cents;
        }

        String plate = activeTrip == null ? null : activeTrip.plate();
        Instant entryAt = activeTrip == null ? null : activeTrip.entryAt();
        return new TollPriceResolved(entry, exit, plate, entryAt, amount, "EUR");
    }

    private TripRepository.ActiveTripRow resolveActiveTrip(Map<String, Object> body) {
        String ticketId = Json.getString(body, "ticketId");
        if (ticketId != null && !ticketId.isBlank()) {
            return tripRepo.findActiveTripByTicket(ticketId);
        }

        String telepassId = Json.getString(body, "telepassId");
        if (telepassId != null && !telepassId.isBlank()) {
            return tripRepo.findActiveTripByTelepass(telepassId);
        }

        return null;
    }

    private TripMetrics computeTripMetrics(String entryTollboothId, String exitTollboothId, Map<String, Object> body) {
        if (tollboothRepo == null) {
            return new TripMetrics(null, false);
        }
        TollboothRepository.TollboothRow entry = tollboothRepo.findById(entryTollboothId);
        TollboothRepository.TollboothRow exit = tollboothRepo.findById(exitTollboothId);
        if (entry == null || exit == null) {
            return new TripMetrics(null, false);
        }
        if (entry.roadCode() == null || exit.roadCode() == null || !entry.roadCode().equals(exit.roadCode())) {
            return new TripMetrics(null, false);
        }
        if (entry.kmMarker() == null || exit.kmMarker() == null) {
            return new TripMetrics(null, false);
        }

        Instant exitAt = parseTimestamp(body);
        Instant entryAt = resolveEntryAt(body);
        if (entryAt == null || !exitAt.isAfter(entryAt)) {
            return new TripMetrics(null, false);
        }

        double distanceKm = Math.abs(exit.kmMarker() - entry.kmMarker());
        double hours = (exitAt.toEpochMilli() - entryAt.toEpochMilli()) / 3_600_000d;
        if (hours <= 0d) {
            return new TripMetrics(null, false);
        }

        double avgSpeed = round2(distanceKm / hours);
        return new TripMetrics(avgSpeed, avgSpeed > 130d);
    }

    private Instant resolveEntryAt(Map<String, Object> body) {
        String ticketId = Json.getString(body, "ticketId");
        if (ticketId != null && !ticketId.isBlank()) {
            TripRepository.ActiveTripRow row = tripRepo.findActiveTripByTicket(ticketId);
            return row == null ? null : row.entryAt();
        }
        String telepassId = Json.getString(body, "telepassId");
        if (telepassId != null && !telepassId.isBlank()) {
            TripRepository.ActiveTripRow row = tripRepo.findActiveTripByTelepass(telepassId);
            return row == null ? null : row.entryAt();
        }
        return null;
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    private Instant parseTimestamp(Map<String, Object> body) {
        String ts = Json.getString(body, "timestamp");
        try {
            return ts == null ? Instant.now() : Instant.parse(ts);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private record TripMetrics(Double avgSpeedKmh, boolean speeding) {}
}
