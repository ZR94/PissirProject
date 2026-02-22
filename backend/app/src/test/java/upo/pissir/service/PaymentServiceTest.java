package upo.pissir.service;

import org.junit.jupiter.api.Test;
import upo.pissir.dto.PaymentResponse;
import upo.pissir.repo.TelepassDebtRepository;
import upo.pissir.repo.TripRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentServiceTest {

    @Test
    void listDebtsByTelepassReturnsRows() {
        PaymentService service = new PaymentService(new FakeDebtRepo(), new FakeTripRepo());

        List<PaymentResponse> rows = service.listDebtsByTelepass("TP-01");
        assertEquals(1, rows.size());
        assertEquals("TP-01", rows.get(0).telepassId());
    }

    @Test
    void payDebtMarksDebtAndTrip() {
        FakeDebtRepo debtRepo = new FakeDebtRepo();
        FakeTripRepo tripRepo = new FakeTripRepo();
        PaymentService service = new PaymentService(debtRepo, tripRepo);

        PaymentResponse paid = service.payDebt(1L);
        assertEquals("PAID", paid.status());
        assertTrue(tripRepo.markPaidCalled);
    }

    @Test
    void payDebtValidatesId() {
        PaymentService service = new PaymentService(new FakeDebtRepo(), new FakeTripRepo());
        assertThrows(IllegalArgumentException.class, () -> service.payDebt(0));
    }

    @Test
    void summaryReturnsAggregates() {
        PaymentService service = new PaymentService(new FakeDebtRepo(), new FakeTripRepo());
        Map<String, Object> summary = service.summary();

        assertEquals("EUR", summary.get("currency"));
        assertEquals(100L, summary.get("openDebtCents"));
        assertEquals(500L, summary.get("collectedCents"));
    }

    private static final class FakeDebtRepo extends TelepassDebtRepository {
        private DebtRow row = new DebtRow(1L, "TP-01", 11L, 100, "EUR", "OPEN", Instant.parse("2026-01-01T00:00:00Z"));

        private FakeDebtRepo() {
            super(null);
        }

        @Override
        public List<DebtRow> findByTelepassId(String telepassId) {
            return List.of(new DebtRow(row.id(), telepassId, row.tripId(), row.amountCents(), row.currency(), row.status(), row.createdAt()));
        }

        @Override
        public Long markDebtPaid(long debtId) {
            if (debtId != row.id()) return null;
            row = new DebtRow(row.id(), row.telepassId(), row.tripId(), row.amountCents(), row.currency(), "PAID", row.createdAt());
            return row.tripId();
        }

        @Override
        public DebtRow findById(long debtId) {
            return debtId == row.id() ? row : null;
        }

        @Override
        public long sumOpenDebtCents() {
            return 100L;
        }
    }

    private static final class FakeTripRepo extends TripRepository {
        private boolean markPaidCalled = false;

        private FakeTripRepo() {
            super(null);
        }

        @Override
        public boolean markTripPaid(long tripId) {
            markPaidCalled = true;
            return true;
        }

        @Override
        public long sumCollectedCents() {
            return 500L;
        }
    }
}
