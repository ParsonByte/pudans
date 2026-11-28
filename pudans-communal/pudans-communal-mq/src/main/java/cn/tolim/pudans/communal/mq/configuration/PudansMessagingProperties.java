package cn.tolim.pudans.communal.mq.configuration;

import cn.tolim.pudans.communal.mq.retry.RetryPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pudans.messaging")
public class PudansMessagingProperties {

    private boolean enabled = true;

    // Provider selection; in-memory is the default when no broker provider is configured
    private String broker = "in-memory";

    private String brokerName = "in-memory";

    private int maxAttempts = 3;

    private Duration initialBackoff = Duration.ofMillis(500);

    private Duration maxBackoff = Duration.ofSeconds(30);

    private double backoffMultiplier = 2.0d;

    private int deadLetterCapacity = 1000;

    public RetryPolicy toRetryPolicy() {
        return new RetryPolicy(maxAttempts, initialBackoff, backoffMultiplier, maxBackoff);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBroker() {
        return broker;
    }

    public void setBroker(String broker) {
        if (broker == null || broker.isBlank()) {
            throw new IllegalArgumentException("broker must not be blank");
        }
        this.broker = broker;
    }

    public String getBrokerName() {
        return brokerName;
    }

    public void setBrokerName(String brokerName) {
        if (brokerName == null || brokerName.isBlank()) {
            throw new IllegalArgumentException("brokerName must not be blank");
        }
        this.brokerName = brokerName;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be greater than or equal to 1");
        }
        this.maxAttempts = maxAttempts;
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public void setInitialBackoff(Duration initialBackoff) {
        if (initialBackoff == null || initialBackoff.isNegative()) {
            throw new IllegalArgumentException("initialBackoff must not be negative");
        }
        this.initialBackoff = initialBackoff;
    }

    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    public void setMaxBackoff(Duration maxBackoff) {
        if (maxBackoff == null || maxBackoff.isNegative()) {
            throw new IllegalArgumentException("maxBackoff must not be negative");
        }
        this.maxBackoff = maxBackoff;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public void setBackoffMultiplier(double backoffMultiplier) {
        if (backoffMultiplier < 1.0d) {
            throw new IllegalArgumentException("backoffMultiplier must be greater than or equal to 1.0");
        }
        this.backoffMultiplier = backoffMultiplier;
    }

    public int getDeadLetterCapacity() {
        return deadLetterCapacity;
    }

    public void setDeadLetterCapacity(int deadLetterCapacity) {
        if (deadLetterCapacity < 1) {
            throw new IllegalArgumentException("deadLetterCapacity must be greater than or equal to 1");
        }
        this.deadLetterCapacity = deadLetterCapacity;
    }
}