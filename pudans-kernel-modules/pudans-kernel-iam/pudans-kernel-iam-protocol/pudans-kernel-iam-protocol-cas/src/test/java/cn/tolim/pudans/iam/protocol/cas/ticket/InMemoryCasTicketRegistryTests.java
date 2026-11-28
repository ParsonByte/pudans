package cn.tolim.pudans.iam.protocol.cas.ticket;

import cn.tolim.pudans.iam.protocol.cas.model.CasServiceTicket;
import cn.tolim.pudans.iam.protocol.cas.model.CasTicketGrantingTicket;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;


class InMemoryCasTicketRegistryTests {

    private final InMemoryCasTicketRegistry registry = new InMemoryCasTicketRegistry();

    @Test
    void storesAndRemovesTicketGrantingTicket() {
        CasTicketGrantingTicket ticket = CasTicketGrantingTicket.builder()
                .id("TGT-1")
                .appCode("app")
                .username("alice")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        registry.saveTicketGrantingTicket(ticket);

        assertThat(registry.getTicketGrantingTicket("TGT-1")).isNotNull();
        registry.removeTicketGrantingTicket("TGT-1");
        assertThat(registry.getTicketGrantingTicket("TGT-1")).isNull();
    }

    @Test
    void returnsNullForExpiredTicketGrantingTicket() {
        CasTicketGrantingTicket ticket = CasTicketGrantingTicket.builder()
                .id("TGT-expired")
                .appCode("app")
                .issuedAt(Instant.now().minusSeconds(120))
                .expiresAt(Instant.now().minusSeconds(60))
                .build();
        registry.saveTicketGrantingTicket(ticket);

        assertThat(registry.getTicketGrantingTicket("TGT-expired")).isNull();
    }

    @Test
    void consumesServiceTicketOnlyOnce() {
        CasServiceTicket ticket = CasServiceTicket.builder()
                .id("ST-1")
                .appCode("app")
                .serviceUrl("https://app.test/callback")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        registry.saveServiceTicket(ticket);

        assertThat(registry.consumeServiceTicket("ST-1")).isNotNull();
        assertThat(registry.consumeServiceTicket("ST-1")).isNull();
    }
}