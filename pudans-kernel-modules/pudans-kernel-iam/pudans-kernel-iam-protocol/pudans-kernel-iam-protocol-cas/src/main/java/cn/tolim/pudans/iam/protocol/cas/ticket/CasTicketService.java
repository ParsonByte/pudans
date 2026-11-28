package cn.tolim.pudans.iam.protocol.cas.ticket;

import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.protocol.cas.model.CasServiceValidationResult;
import cn.tolim.pudans.iam.protocol.cas.model.CasTicketGrantingTicket;


public interface CasTicketService {

    // Create a ticket granting ticket for the authenticated user.
    String createTicketGrantingTicket(String appCode, UserDetails user);

    // Grant a service ticket for the given service URL.
    String grantServiceTicket(String appCode, String ticketGrantingTicketId, String serviceUrl,
                              boolean renew);

    // Validate and consume a service ticket.
    CasServiceValidationResult validateServiceTicket(String appCode, String serviceUrl,
                                                     String ticketId);

    // Destroy a ticket granting ticket.
    void destroyTicketGrantingTicket(String ticketGrantingTicketId);

    // Find a ticket granting ticket.
    CasTicketGrantingTicket getTicketGrantingTicket(String ticketGrantingTicketId);
}