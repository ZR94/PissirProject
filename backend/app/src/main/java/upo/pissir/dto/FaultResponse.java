package upo.pissir.dto;

public record FaultResponse(
        long id,
        String tollboothId,
        String direction,
        String channel,
        String code,
        String message,
        String severity,
        String status,
        String backendAction,
        String createdAt,
        String respondedAt
) {}
