package upo.pissir.dto;

public record TripResponse(
        long id,
        String entryTollboothId,
        String exitTollboothId,
        String ticketId,
        String telepassId,
        String plate,
        String entryAt,
        String exitAt,
        Integer amountCents,
        String currency,
        boolean paid
) {}
