package cn.tolim.pudans.infra.redis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AiQuotaWindowStore {

    private static final String KEY_PREFIX = "pudans:ai:quota:";

    private static final long CREDIT_SCALE = 10_000L;

    private static final Duration HASH_RETENTION = Duration.ofDays(8);

    private static final RedisScript<Long> RESERVE_SCRIPT = RedisScript.of("""
            local function remove_expired(set_key, hash_key, now_ms)
              local expired = redis.call('ZRANGEBYSCORE', set_key, '-inf', now_ms)
              for _, member in ipairs(expired) do
                redis.call('ZREM', set_key, member)
                redis.call('HDEL', hash_key, member)
              end
            end
            local function sum_values(hash_key)
              local credits, tools, mcp = 0, 0, 0
              for _, value in ipairs(redis.call('HVALS', hash_key)) do
                local c, t, m = string.match(value, '([^,]+),([^,]+),([^,]+)')
                if c then
                  credits = credits + tonumber(c)
                  tools = tools + tonumber(t)
                  mcp = mcp + tonumber(m)
                end
              end
              return credits, tools, mcp
            end
            local now_ms = tonumber(ARGV[1])
            local five_expiry = tonumber(ARGV[2])
            local weekly_expiry = tonumber(ARGV[3])
            remove_expired(KEYS[1], KEYS[2], now_ms)
            remove_expired(KEYS[3], KEYS[4], now_ms)
            local five_credits, five_tools, five_mcp = sum_values(KEYS[2])
            local weekly_credits, weekly_tools, weekly_mcp = sum_values(KEYS[4])
            local demand_credits = tonumber(ARGV[4])
            local demand_tools = tonumber(ARGV[5])
            local demand_mcp = tonumber(ARGV[6])
            local five_limit_credits = tonumber(ARGV[7])
            local five_limit_tools = tonumber(ARGV[8])
            local five_limit_mcp = tonumber(ARGV[9])
            local weekly_limit_credits = tonumber(ARGV[10])
            local weekly_limit_tools = tonumber(ARGV[11])
            local weekly_limit_mcp = tonumber(ARGV[12])
            local five_usage = tonumber(ARGV[13])
            local five_tool_usage = tonumber(ARGV[14])
            local five_mcp_usage = tonumber(ARGV[15])
            local weekly_usage = tonumber(ARGV[16])
            local weekly_tool_usage = tonumber(ARGV[17])
            local weekly_mcp_usage = tonumber(ARGV[18])
            if five_usage + five_credits + demand_credits > five_limit_credits then return 0 end
            if five_tool_usage + five_tools + demand_tools > five_limit_tools then return 0 end
            if five_mcp_usage + five_mcp + demand_mcp > five_limit_mcp then return 0 end
            if weekly_usage + weekly_credits + demand_credits > weekly_limit_credits then return 0 end
            if weekly_tool_usage + weekly_tools + demand_tools > weekly_limit_tools then return 0 end
            if weekly_mcp_usage + weekly_mcp + demand_mcp > weekly_limit_mcp then return 0 end
            local member = ARGV[19]
            local amount = ARGV[20]
            redis.call('ZADD', KEYS[1], five_expiry, member)
            redis.call('ZADD', KEYS[3], weekly_expiry, member)
            redis.call('HSET', KEYS[2], member, amount)
            redis.call('HSET', KEYS[4], member, amount)
            redis.call('PEXPIRE', KEYS[2], ARGV[21])
            redis.call('PEXPIRE', KEYS[4], ARGV[22])
            return 1
            """, Long.class);

    private static final RedisScript<Long> REMOVE_SCRIPT = RedisScript.of("""
            redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('HDEL', KEYS[2], ARGV[1])
            redis.call('ZREM', KEYS[3], ARGV[1])
            redis.call('HDEL', KEYS[4], ARGV[1])
            return 1
            """, Long.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    public AiQuotaWindowStore(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
    }

    public Mono<ReservationResult> reserve(
            Scope scope,
            Limit fiveHourLimit,
            Limit weeklyLimit,
            Amount settledFiveHour,
            Amount settledWeekly,
            Amount demand,
            UUID reservationId) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(fiveHourLimit, "fiveHourLimit");
        Objects.requireNonNull(weeklyLimit, "weeklyLimit");
        Objects.requireNonNull(settledFiveHour, "settledFiveHour");
        Objects.requireNonNull(settledWeekly, "settledWeekly");
        Objects.requireNonNull(demand, "demand");
        Objects.requireNonNull(reservationId, "reservationId");
        var keys = keys(scope);
        var now = Instant.now();
        var fiveExpiry = scope.fiveHourBucketAt().plus(Duration.ofHours(5));
        var weeklyExpiry = scope.weeklyPeriodStart().plus(Duration.ofDays(7));
        var hashTtl = HASH_RETENTION.toMillis();
        var amount = amount(demand);
        return redisTemplate
                .execute(
                        RESERVE_SCRIPT,
                        keys,
                        List.of(
                                now.toEpochMilli(),
                                fiveExpiry.toEpochMilli(),
                                weeklyExpiry.toEpochMilli(),
                                amount.credits(),
                                demand.toolCalls(),
                                demand.mcpCalls(),
                                micros(fiveHourLimit.credits()),
                                fiveHourLimit.toolCalls(),
                                fiveHourLimit.mcpCalls(),
                                micros(weeklyLimit.credits()),
                                weeklyLimit.toolCalls(),
                                weeklyLimit.mcpCalls(),
                                micros(settledFiveHour.credits()),
                                settledFiveHour.toolCalls(),
                                settledFiveHour.mcpCalls(),
                                micros(settledWeekly.credits()),
                                settledWeekly.toolCalls(),
                                settledWeekly.mcpCalls(),
                                reservationId.toString(),
                                amount.serialized(),
                                hashTtl,
                                hashTtl))
                .next()
                .map(value -> value != null && value > 0
                        ? ReservationResult.RESERVED
                        : ReservationResult.INSUFFICIENT)
                .defaultIfEmpty(ReservationResult.INSUFFICIENT);
    }

    public Mono<Void> settle(Scope scope, UUID reservationId) {
        return remove(scope, reservationId);
    }

    public Mono<Void> release(Scope scope, UUID reservationId) {
        return remove(scope, reservationId);
    }

    private Mono<Void> remove(Scope scope, UUID reservationId) {
        return redisTemplate
                .execute(REMOVE_SCRIPT, keys(scope), List.of(reservationId.toString()))
                .next()
                .then();
    }

    private static List<String> keys(Scope scope) {
        var subject = scope.subjectType() + ":" + scope.subjectId();
        var five = KEY_PREFIX + "five:" + subject;
        var weekly = KEY_PREFIX + "weekly:" + subject + ":" + scope.weeklyPeriodStart().toEpochMilli();
        return List.of(five + ":events", five + ":amounts", weekly + ":events", weekly + ":amounts");
    }

    private static long micros(BigDecimal credits) {
        var value = credits == null ? BigDecimal.ZERO : credits;
        return value.multiply(BigDecimal.valueOf(CREDIT_SCALE))
                .setScale(0, RoundingMode.UP)
                .longValueExact();
    }

    private static AmountValue amount(Amount amount) {
        return new AmountValue(micros(amount.credits()), amount.toolCalls(), amount.mcpCalls());
    }

    private record AmountValue(long credits, int toolCalls, int mcpCalls) {

        String serialized() {
            return credits + "," + toolCalls + "," + mcpCalls;
        }
    }

    public enum ReservationResult {
        RESERVED,
        INSUFFICIENT
    }

    public record Scope(
            String subjectType,
            String subjectId,
            Instant fiveHourBucketAt,
            Instant weeklyPeriodStart) {

        public Scope {
            if (subjectType == null || subjectType.isBlank()
                    || subjectId == null || subjectId.isBlank()) {
                throw new IllegalArgumentException("subjectType and subjectId must not be blank");
            }
            Objects.requireNonNull(fiveHourBucketAt, "fiveHourBucketAt");
            Objects.requireNonNull(weeklyPeriodStart, "weeklyPeriodStart");
        }
    }

    public record Limit(BigDecimal credits, int toolCalls, int mcpCalls) {

        public Limit {
            if (credits == null || credits.signum() < 0 || toolCalls < 0 || mcpCalls < 0) {
                throw new IllegalArgumentException("quota limits must not be negative");
            }
        }
    }

    public record Amount(BigDecimal credits, int toolCalls, int mcpCalls) {

        public Amount {
            if (credits == null || credits.signum() < 0 || toolCalls < 0 || mcpCalls < 0) {
                throw new IllegalArgumentException("quota amounts must not be negative");
            }
        }
    }
}
