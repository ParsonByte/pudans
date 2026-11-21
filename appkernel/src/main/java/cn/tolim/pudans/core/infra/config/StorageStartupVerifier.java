package cn.tolim.pudans.core.infra.config;

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
                throw new IllegalStateException("PostgreSQL 不可达：未能创建 R2DBC 连接。");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "必须使用 PostgreSQL，但 R2DBC 连接失败。请检查 spring.r2dbc.url 和凭据。",
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
                throw new IllegalStateException("Redis 不可达：键查询没有响应。");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "会话和权限需要 Redis，但连接失败。请检查 spring.data.redis。", e);
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
