package upo.pissir.dto;

public record RouteStatsResponse(
        String entryTollboothId,
        String exitTollboothId,
        long tripsCount,
        long paidTripsCount,
        long unpaidTripsCount,
        long totalAmountCents,
        long avgAmountCents,
        String currency
) {}
