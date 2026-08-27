package cn.tolim.pudans.infra.config;

import io.r2dbc.spi.ConnectionFactory;
import java.time.Duration;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;


@Component
public class StorageStartupVerifier implements SmartLifecycle {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ConnectionFactory connectionFactory;

    private final ReactiveStringRedisTemplate redisTemplate;

    private volatile boolean running;

    public StorageStartupVerifier(
            ConnectionFactory connectionFactory, ReactiveStringRedisTemplate redisTemplate) {
        this.connectionFactory = connectionFactory;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void start() {
        verifyPostgres();
        verifyRedis();
        running = true;
    }

    private void verifyPostgres() {
        io.r2dbc.spi.Connection connection = null;
        try {
            connection = reactor.core.publisher.Mono.from(connectionFactory.create()).block(TIMEOUT);
            if (connection == null) {
                throw new IllegalStateException("PostgreSQL is unreachable: no R2DBC connection was created.");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "PostgreSQL is required but the R2DBC connection failed. Check spring.r2dbc.url and credentials.",
                    e);
        } finally {
            if (connection != null) {
                reactor.core.publisher.Mono.from(connection.close()).block(TIMEOUT);
            }
        }
    }

    private void verifyRedis() {
        try {
            var exists = redisTemplate.hasKey("pudans:startup:probe").block(TIMEOUT);
            if (exists == null) {
                throw new IllegalStateException("Redis is unreachable: no response to a key lookup.");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Redis is required for sessions and permissions but the connection failed. Check spring.data"
                            + ".redis.",
                    e);
        }
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE;
    }
}
