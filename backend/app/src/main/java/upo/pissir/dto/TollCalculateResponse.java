package upo.pissir.dto;

public record TollCalculateResponse(String entryTollboothId, String exitTollboothId, int amountCents, String currency) {}
