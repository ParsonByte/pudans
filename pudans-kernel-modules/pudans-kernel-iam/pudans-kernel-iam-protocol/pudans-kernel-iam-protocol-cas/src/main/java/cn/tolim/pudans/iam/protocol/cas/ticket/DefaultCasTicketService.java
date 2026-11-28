package cn.tolim.pudans.iam.protocol.cas.ticket;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.protocol.cas.exception.CasErrorCodes;
import cn.tolim.pudans.iam.protocol.cas.exception.CasProtocolException;
import cn.tolim.pudans.iam.protocol.cas.model.CasProtocolConfig;
import cn.tolim.pudans.iam.protocol.cas.model.CasServiceTicket;
import cn.tolim.pudans.iam.protocol.cas.model.CasServiceValidationResult;
import cn.tolim.pudans.iam.protocol.cas.model.CasTicketGrantingTicket;
import cn.tolim.pudans.iam.protocol.cas.service.CasServiceUrlValidator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.SERVICE_TICKET_PREFIX;
import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.TICKET_GRANTING_TICKET_PREFIX;


public class DefaultCasTicketService extends AbstractCasTicketService {

    private final CasTicketRegistry ticketRegistry;

    public DefaultCasTicketService(CasTicketRegistry ticketRegistry,
                                   ApplicationServiceLoader applicationServiceLoader) {
        super(applicationServiceLoader);
        Assert.notNull(ticketRegistry, "ticketRegistry must not be null");
        this.ticketRegistry = ticketRegistry;
    }

    @Override
    public String createTicketGrantingTicket(String appCode, UserDetails user) {
        Assert.hasText(appCode, "appCode must not be blank");
        Assert.notNull(user, "user must not be null");
        CasProtocolConfig config = getProtocolConfig(appCode);
        Instant now = Instant.now();
        CasTicketGrantingTicket ticket = CasTicketGrantingTicket.builder()
                .id(TICKET_GRANTING_TICKET_PREFIX + UUID.randomUUID()
                        .toString().replace("-", ""))
                .appCode(appCode)
                .userId(user.getId())
                .username(user.getUsername())
                .attributes(CasUserAttributes.resolve(user, config.getAttributeMappings()))
                .issuedAt(now)
                .expiresAt(now.plus(config.getTicketGrantingTicketTimeToLive()))
                .build();
        ticketRegistry.saveTicketGrantingTicket(ticket);
        return ticket.getId();
    }

    @Override
    public String grantServiceTicket(String appCode, String ticketGrantingTicketId,
                                     String serviceUrl, boolean renew) {
        Assert.hasText(appCode, "appCode must not be blank");
        Assert.hasText(ticketGrantingTicketId, "ticketGrantingTicketId must not be blank");
        if (StringUtils.isBlank(serviceUrl)) {
            throw new CasProtocolException(CasErrorCodes.INVALID_REQUEST,
                    "Service URL is missing");
        }
        CasProtocolConfig config = getProtocolConfig(appCode);
        CasTicketGrantingTicket grantingTicket = ticketRegistry
                .getTicketGrantingTicket(ticketGrantingTicketId);
        if (grantingTicket == null || !appCode.equals(grantingTicket.getAppCode())) {
            throw new CasProtocolException(CasErrorCodes.INVALID_TICKET,
                    "Ticket granting ticket is invalid or expired");
        }
        if (renew && !Boolean.TRUE.equals(config.getRenewEnabled())) {
            throw new CasProtocolException(CasErrorCodes.INVALID_REQUEST,
                    "Renew authentication is not enabled");
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(config.getTicketTimeToLive());
        if (grantingTicket.getExpiresAt() != null
                && grantingTicket.getExpiresAt().isBefore(expiresAt)) {
            expiresAt = grantingTicket.getExpiresAt();
        }
        CasServiceTicket ticket = CasServiceTicket.builder()
                .id(SERVICE_TICKET_PREFIX + UUID.randomUUID().toString().replace("-", ""))
                .ticketGrantingTicketId(grantingTicket.getId())
                .appCode(appCode)
                .serviceUrl(serviceUrl)
                .userId(grantingTicket.getUserId())
                .username(grantingTicket.getUsername())
                .attributes(grantingTicket.getAttributes())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .renew(renew)
                .build();
        ticketRegistry.saveServiceTicket(ticket);
        return ticket.getId();
    }

    @Override
    public CasServiceValidationResult validateServiceTicket(String appCode, String serviceUrl,
                                                            String ticketId) {
        if (StringUtils.isBlank(ticketId)) {
            return CasServiceValidationResult.failure(CasErrorCodes.INVALID_REQUEST,
                    "Ticket is missing");
        }
        CasServiceTicket ticket = ticketRegistry.consumeServiceTicket(ticketId);
        if (ticket == null || ticket.isExpired() || !appCode.equals(ticket.getAppCode())) {
            return CasServiceValidationResult.failure(CasErrorCodes.INVALID_TICKET,
                    "Ticket is invalid or expired");
        }
        String issuedServiceUrl = CasServiceUrlValidator.normalize(ticket.getServiceUrl());
        String requestedServiceUrl = CasServiceUrlValidator.normalize(serviceUrl);
        if (issuedServiceUrl == null || !issuedServiceUrl.equals(requestedServiceUrl)) {
            return CasServiceValidationResult.failure(CasErrorCodes.INVALID_SERVICE,
                    "Service URL does not match the ticket");
        }
        return CasServiceValidationResult.success(ticket.getUsername(),
                ticket.getAttributes() == null ? Map.of() : ticket.getAttributes());
    }

    @Override
    public void destroyTicketGrantingTicket(String ticketGrantingTicketId) {
        ticketRegistry.removeTicketGrantingTicket(ticketGrantingTicketId);
    }

    @Override
    public CasTicketGrantingTicket getTicketGrantingTicket(String ticketGrantingTicketId) {
        return ticketRegistry.getTicketGrantingTicket(ticketGrantingTicketId);
    }
}