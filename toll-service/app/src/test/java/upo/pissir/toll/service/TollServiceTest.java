package upo.pissir.toll.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TollServiceTest {

    @Test
    void tollPriceReplyTopicUsesProvidedChannel() {
        TollService service = new TollService(null, null, new SessionStore(), "MI_Ovest");

        assertEquals("highway/MI_Ovest/exit/manual/responses", service.tollPriceReplyTopic("manual"));
        assertEquals("highway/MI_Ovest/exit/telepass/responses", service.tollPriceReplyTopic("telepass"));
    }
}
