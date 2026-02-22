package upo.pissir.service;

import upo.pissir.dto.PaymentResponse;
import upo.pissir.repo.TelepassDebtRepository;
import upo.pissir.repo.TripRepository;

import java.util.List;
import java.util.Map;

public class PaymentService {
    private final TelepassDebtRepository debtRepo;
    private final TripRepository tripRepo;

    public PaymentService(TelepassDebtRepository debtRepo, TripRepository tripRepo) {
        this.debtRepo = debtRepo;
        this.tripRepo = tripRepo;
    }

    public List<PaymentResponse> listDebtsByTelepass(String telepassId) {
        if (telepassId == null || telepassId.isBlank()) {
            throw new IllegalArgumentException("telepassId is required");
        }
        return debtRepo.findByTelepassId(telepassId).stream().map(this::toResponse).toList();
    }

    public PaymentResponse payDebt(long debtId) {
        if (debtId <= 0) {
            throw new IllegalArgumentException("debtId must be positive");
        }

        Long tripId = debtRepo.markDebtPaid(debtId);
        if (tripId == null) {
            throw new IllegalStateException("debt not found or already paid");
        }
        tripRepo.markTripPaid(tripId);

        TelepassDebtRepository.DebtRow row = debtRepo.findById(debtId);
        if (row == null) {
            throw new IllegalStateException("debt not found after payment");
        }
        return toResponse(row);
    }

    public Map<String, Object> summary() {
        long openDebtCents = debtRepo.sumOpenDebtCents();
        long collectedCents = tripRepo.sumCollectedCents();
        return Map.of(
                "currency", "EUR",
                "openDebtCents", openDebtCents,
                "collectedCents", collectedCents
        );
    }

    private PaymentResponse toResponse(TelepassDebtRepository.DebtRow row) {
        return new PaymentResponse(
                row.id(),
                row.telepassId(),
                row.tripId(),
                row.amountCents(),
                row.currency(),
                row.status(),
                row.createdAt() == null ? null : row.createdAt().toString()
        );
    }
}
