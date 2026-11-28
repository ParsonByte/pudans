package cn.tolim.pudans.iam.protocol.cas.ticket;

import cn.tolim.pudans.iam.protocol.cas.model.CasServiceTicket;
import cn.tolim.pudans.iam.protocol.cas.model.CasTicketGrantingTicket;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public class InMemoryCasTicketRegistry implements CasTicketRegistry {

    private final ConcurrentMap<String, CasTicketGrantingTicket> ticketGrantingTickets =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CasServiceTicket> serviceTickets =
            new ConcurrentHashMap<>();

    @Override
    public void saveTicketGrantingTicket(CasTicketGrantingTicket ticket) {
        Assert.notNull(ticket, "ticket must not be null");
        Assert.hasText(ticket.getId(), "ticket id must not be blank");
        ticketGrantingTickets.put(ticket.getId(), ticket);
        removeExpired();
    }

    @Override
    public CasTicketGrantingTicket getTicketGrantingTicket(String id) {
        if (id == null) {
            return null;
        }
        CasTicketGrantingTicket ticket = ticketGrantingTickets.get(id);
        if (ticket == null) {
            return null;
        }
        if (ticket.isExpired()) {
            ticketGrantingTickets.remove(id, ticket);
            return null;
        }
        return ticket;
    }

    @Override
    public void removeTicketGrantingTicket(String id) {
        if (id != null) {
            ticketGrantingTickets.remove(id);
        }
    }

    @Override
    public void saveServiceTicket(CasServiceTicket ticket) {
        Assert.notNull(ticket, "ticket must not be null");
        Assert.hasText(ticket.getId(), "ticket id must not be blank");
        serviceTickets.put(ticket.getId(), ticket);
        removeExpired();
    }

    @Override
    public CasServiceTicket consumeServiceTicket(String id) {
        if (id == null) {
            return null;
        }
        CasServiceTicket ticket = serviceTickets.remove(id);
        if (ticket == null || ticket.isExpired()) {
            return null;
        }
        return ticket;
    }

    @Override
    public void removeServiceTicket(String id) {
        if (id != null) {
            serviceTickets.remove(id);
        }
    }

    private void removeExpired() {
        Instant now = Instant.now();
        ticketGrantingTickets.entrySet()
                .removeIf(entry -> entry.getValue().getExpiresAt() != null
                        && !entry.getValue().getExpiresAt().isAfter(now));
        serviceTickets.entrySet()
                .removeIf(entry -> entry.getValue().getExpiresAt() != null
                        && !entry.getValue().getExpiresAt().isAfter(now));
    }
}