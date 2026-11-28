package cn.tolim.pudans.iam.protocol.cas.ticket;

import cn.tolim.pudans.iam.protocol.cas.model.CasServiceTicket;
import cn.tolim.pudans.iam.protocol.cas.model.CasTicketGrantingTicket;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Setter;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;

import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.CAS_PROTOCOL_CACHE_PREFIX;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;


public class RedisCasTicketRegistry implements CasTicketRegistry {

    private static final String TICKET_GRANTING_TICKET_KEY = "tgt:";
    private static final String SERVICE_TICKET_KEY = "st:";

    private final RedisOperations<String, String> redisOperations;
    @Setter
    private String prefix = CAS_PROTOCOL_CACHE_PREFIX;
    @Setter
    private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(WRITE_DATES_AS_TIMESTAMPS);

    public RedisCasTicketRegistry(RedisOperations<String, String> redisOperations) {
        Assert.notNull(redisOperations, "redisOperations must not be null");
        this.redisOperations = redisOperations;
    }

    @Override
    public void saveTicketGrantingTicket(CasTicketGrantingTicket ticket) {
        Duration ttl = remaining(ticket.getIssuedAt(), ticket.getExpiresAt());
        redisOperations.opsForValue().set(tgtKey(ticket.getId()), write(ticket), ttl);
    }

    @Override
    public CasTicketGrantingTicket getTicketGrantingTicket(String id) {
        if (id == null) {
            return null;
        }
        CasTicketGrantingTicket ticket = readTgt(redisOperations.opsForValue().get(tgtKey(id)));
        if (ticket == null || ticket.isExpired()) {
            return null;
        }
        return ticket;
    }

    @Override
    public void removeTicketGrantingTicket(String id) {
        if (id != null) {
            redisOperations.delete(tgtKey(id));
        }
    }

    @Override
    public void saveServiceTicket(CasServiceTicket ticket) {
        Duration ttl = remaining(ticket.getIssuedAt(), ticket.getExpiresAt());
        redisOperations.opsForValue().set(stKey(ticket.getId()), write(ticket), ttl);
    }

    @Override
    public CasServiceTicket consumeServiceTicket(String id) {
        if (id == null) {
            return null;
        }
        CasServiceTicket ticket = readSt(redisOperations.opsForValue().getAndDelete(stKey(id)));
        if (ticket == null || ticket.isExpired()) {
            return null;
        }
        return ticket;
    }

    @Override
    public void removeServiceTicket(String id) {
        if (id != null) {
            redisOperations.delete(stKey(id));
        }
    }

    private Duration remaining(Instant issuedAt, Instant expiresAt) {
        if (expiresAt == null) {
            return Duration.ofMinutes(5);
        }
        Instant start = issuedAt == null ? Instant.now() : issuedAt;
        Duration ttl = Duration.between(start, expiresAt);
        return ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(1) : ttl;
    }

    private String tgtKey(String id) {
        return prefix + TICKET_GRANTING_TICKET_KEY + id;
    }

    private String stKey(String id) {
        return prefix + SERVICE_TICKET_KEY + id;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    private CasTicketGrantingTicket readTgt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, CasTicketGrantingTicket.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    private CasServiceTicket readSt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, CasServiceTicket.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }
}