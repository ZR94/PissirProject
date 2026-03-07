package upo.pissir.dto;

public record UpdateTollboothRequest(
        String roadCode,
        Double kmMarker,
        String region,
        String description
) {}
