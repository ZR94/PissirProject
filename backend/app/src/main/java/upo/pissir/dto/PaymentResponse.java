package upo.pissir.dto;

public record PaymentResponse(
        long id,
        String telepassId,
        long tripId,
        int amountCents,
        String currency,
        String status,
        String createdAt
) {}
