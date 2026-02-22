package upo.pissir.dto;

public record CreateFareRequest(String entryTollboothId, String exitTollboothId, Integer amountCents) {}