package upo.pissir.dto;

public record TollboothResponse(
        String id,
        String roadCode,
        Double kmMarker,
        String region,
        String description
) {}
