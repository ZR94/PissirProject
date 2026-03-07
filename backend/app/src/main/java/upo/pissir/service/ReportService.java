package upo.pissir.service;

import upo.pissir.dto.ActiveTripResponse;
import upo.pissir.dto.RouteStatsResponse;
import upo.pissir.dto.TripResponse;
import upo.pissir.repo.TripRepository;

import java.time.Instant;
import java.time.Duration;
import java.util.List;

public class ReportService {
    private final TripRepository tripRepo;

    private record ReportFilter(
            Instant from,
            Instant to,
            String entryTollboothId,
            String exitTollboothId,
            String channel,
            Boolean paid,
            int limit
    ) {}

    public ReportService(TripRepository tripRepo) {
        this.tripRepo = tripRepo;
    }

    public List<TripResponse> listTrips(
            String fromRaw,
            String toRaw,
            String entryTollboothId,
            String exitTollboothId,
            String channelRaw,
            String paidRaw,
            String limitRaw
    ) {
        ReportFilter filter = parseFilter(fromRaw, toRaw, entryTollboothId, exitTollboothId, channelRaw, paidRaw, limitRaw);
        return tripRepo.findTripsForReport(
                filter.from(),
                filter.to(),
                filter.entryTollboothId(),
                filter.exitTollboothId(),
                filter.channel(),
                filter.paid(),
                filter.limit()
        );
    }

    public List<RouteStatsResponse> listRouteStats(
            String fromRaw,
            String toRaw,
            String entryTollboothId,
            String exitTollboothId,
            String channelRaw,
            String paidRaw
    ) {
        ReportFilter filter = parseFilter(fromRaw, toRaw, entryTollboothId, exitTollboothId, channelRaw, paidRaw, null);
        return tripRepo.findRouteStatsForReport(
                        filter.from(),
                        filter.to(),
                        filter.entryTollboothId(),
                        filter.exitTollboothId(),
                        filter.channel(),
                        filter.paid()
                )
                .stream()
                .map(row -> new RouteStatsResponse(
                        row.entryTollboothId(),
                        row.exitTollboothId(),
                        row.tripsCount(),
                        row.paidTripsCount(),
                        row.unpaidTripsCount(),
                        row.totalAmountCents(),
                        row.avgAmountCents(),
                        "EUR"
                ))
                .toList();
    }

    public List<ActiveTripResponse> listActiveTrips(
            String entryTollboothIdRaw,
            String channelRaw,
            String limitRaw
    ) {
        String entryTollboothId = normalize(entryTollboothIdRaw);
        String channel = normalize(channelRaw);
        if (channel != null && !"manual".equals(channel) && !"telepass".equals(channel)) {
            throw new IllegalArgumentException("channel must be manual or telepass");
        }

        int limit = 100;
        if (limitRaw != null && !limitRaw.isBlank()) {
            try {
                limit = Integer.parseInt(limitRaw);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("limit must be an integer");
            }
        }
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("limit must be between 1 and 500");
        }

        Instant now = Instant.now();
        return tripRepo.findActiveTrips(entryTollboothId, channel, limit)
                .stream()
                .map(row -> toActiveTripResponse(row, now))
                .toList();
    }

    private ReportFilter parseFilter(
            String fromRaw,
            String toRaw,
            String entryTollboothIdRaw,
            String exitTollboothIdRaw,
            String channelRaw,
            String paidRaw,
            String limitRaw
    ) {
        Instant from = parseInstant("from", fromRaw);
        Instant to = parseInstant("to", toRaw);
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must be <= to");
        }

        String channel = normalize(channelRaw);
        if (channel != null && !"manual".equals(channel) && !"telepass".equals(channel)) {
            throw new IllegalArgumentException("channel must be manual or telepass");
        }

        Boolean paid = parseBooleanStrict("paid", paidRaw);

        int limit = 100;
        if (limitRaw != null && !limitRaw.isBlank()) {
            try {
                limit = Integer.parseInt(limitRaw);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("limit must be an integer");
            }
        }
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("limit must be between 1 and 500");
        }

        return new ReportFilter(
                from,
                to,
                normalize(entryTollboothIdRaw),
                normalize(exitTollboothIdRaw),
                channel,
                paid,
                limit
        );
    }

    private static Instant parseInstant(String fieldName, String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + " must be ISO-8601 instant");
        }
    }

    private static Boolean parseBooleanStrict(String fieldName, String raw) {
        if (raw == null || raw.isBlank()) return null;
        if ("true".equalsIgnoreCase(raw)) return true;
        if ("false".equalsIgnoreCase(raw)) return false;
        throw new IllegalArgumentException(fieldName + " must be true or false");
    }

    private static String normalize(String raw) {
        if (raw == null) return null;
        String out = raw.trim();
        return out.isBlank() ? null : out;
    }

    private static ActiveTripResponse toActiveTripResponse(TripRepository.ActiveTripReportRow row, Instant now) {
        String passId = row.ticketId() != null ? row.ticketId() : row.telepassId();
        String channel = row.ticketId() != null ? "manual" : "telepass";
        long minutesInNetwork = 0L;
        if (row.entryAt() != null) {
            minutesInNetwork = Math.max(0L, Duration.between(row.entryAt(), now).toMinutes());
        }
        return new ActiveTripResponse(
                row.id(),
                row.entryTollboothId(),
                passId,
                channel,
                row.plate(),
                row.entryAt() == null ? null : row.entryAt().toString(),
                minutesInNetwork
        );
    }
}
