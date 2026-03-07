package upo.pissir.dto;

public record ActiveTripResponse(
        long id,
        String entryTollboothId,
        String passId,
        String channel,
        String plate,
        String entryAt,
        long minutesInNetwork
) {}
