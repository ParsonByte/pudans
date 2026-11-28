package cn.tolim.pudans.iam.protocol.cas.ticket;

import cn.tolim.pudans.iam.protocol.cas.model.CasServiceTicket;
import cn.tolim.pudans.iam.protocol.cas.model.CasTicketGrantingTicket;


public interface CasTicketRegistry {

    // Save a ticket granting ticket.
    void saveTicketGrantingTicket(CasTicketGrantingTicket ticket);

    // Find a valid ticket granting ticket.
    CasTicketGrantingTicket getTicketGrantingTicket(String id);

    // Remove a ticket granting ticket.
    void removeTicketGrantingTicket(String id);

    // Save a service ticket.
    void saveServiceTicket(CasServiceTicket ticket);

    // Remove and return a service ticket.
    CasServiceTicket consumeServiceTicket(String id);

    // Remove a service ticket.
    void removeServiceTicket(String id);
}