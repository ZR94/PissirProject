package upo.pissir.service;

import upo.pissir.json.Json;
import upo.pissir.repo.FareRepository;
import upo.pissir.repo.TelepassDebtRepository;
import upo.pissir.repo.TripRepository;

import java.time.Instant;
import java.util.Map;

public class TollProcessingService {

    private final FareRepository fareRepo;
    private final TripRepository tripRepo;
    private final TelepassDebtRepository debtRepo;

    public TollProcessingService(FareRepository fareRepo, TripRepository tripRepo, TelepassDebtRepository debtRepo) {
        this.fareRepo = fareRepo;
        this.tripRepo = tripRepo;
        this.debtRepo = debtRepo;
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
            tripRepo.closeTrip(tripId, exitTollboothId, ts, amountCents, true);
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
            tripRepo.closeTrip(tripId, exitTollboothId, ts, amountCents, false);
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

    private Instant parseTimestamp(Map<String, Object> body) {
        String ts = Json.getString(body, "timestamp");
        try {
            return ts == null ? Instant.now() : Instant.parse(ts);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
