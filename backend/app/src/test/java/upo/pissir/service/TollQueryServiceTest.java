package upo.pissir.service;

import org.junit.jupiter.api.Test;
import upo.pissir.dto.TollCalculateResponse;
import upo.pissir.repo.FareRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TollQueryServiceTest {

    @Test
    void calculateReturnsResponseWhenFareExists() {
        TollQueryService service = new TollQueryService(new FakeFareRepository(720));
        TollCalculateResponse response = service.calculate("VC_Est", "MI_Ovest");

        assertEquals("VC_Est", response.entryTollboothId());
        assertEquals("MI_Ovest", response.exitTollboothId());
        assertEquals(720, response.amountCents());
        assertEquals("EUR", response.currency());
    }

    @Test
    void calculateThrowsOnMissingParams() {
        TollQueryService service = new TollQueryService(new FakeFareRepository(720));
        assertThrows(IllegalArgumentException.class, () -> service.calculate("", "MI_Ovest"));
    }

    @Test
    void calculateThrowsWhenFareNotFound() {
        TollQueryService service = new TollQueryService(new FakeFareRepository(null));
        assertThrows(IllegalStateException.class, () -> service.calculate("VC_Est", "MI_Ovest"));
    }

    private static final class FakeFareRepository extends FareRepository {
        private final Integer fare;

        private FakeFareRepository(Integer fare) {
            super(null);
            this.fare = fare;
        }

        @Override
        public Integer findFareCents(String entryTollboothId, String exitTollboothId) {
            return fare;
        }
    }
}
