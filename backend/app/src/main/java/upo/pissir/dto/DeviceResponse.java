package upo.pissir.dto;

public record DeviceResponse(
        long id,
        String tollboothId,
        String direction,
        String channel,
        boolean enabled,
        String createdAt
) {}
