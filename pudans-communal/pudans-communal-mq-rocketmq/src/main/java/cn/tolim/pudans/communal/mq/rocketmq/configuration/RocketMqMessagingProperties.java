package cn.tolim.pudans.communal.mq.rocketmq.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pudans.messaging.rocketmq")
public class RocketMqMessagingProperties {

    private String nameServer = "127.0.0.1:9876";

    private String producerGroup = "pudans-producer";

    private int sendTimeoutMs = 3000;

    private int consumerThreadMin = 5;

    private int consumerThreadMax = 20;

    private String accessKey;

    private String secretKey;

    public String getNameServer() {
        return nameServer;
    }

    public void setNameServer(String nameServer) {
        if (nameServer == null || nameServer.isBlank()) {
            throw new IllegalArgumentException("nameServer must not be blank");
        }
        this.nameServer = nameServer;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        if (producerGroup == null || producerGroup.isBlank()) {
            throw new IllegalArgumentException("producerGroup must not be blank");
        }
        this.producerGroup = producerGroup;
    }

    public int getSendTimeoutMs() {
        return sendTimeoutMs;
    }

    public void setSendTimeoutMs(int sendTimeoutMs) {
        if (sendTimeoutMs < 1) {
            throw new IllegalArgumentException("sendTimeoutMs must be positive");
        }
        this.sendTimeoutMs = sendTimeoutMs;
    }

    public int getConsumerThreadMin() {
        return consumerThreadMin;
    }

    public void setConsumerThreadMin(int consumerThreadMin) {
        if (consumerThreadMin < 1) {
            throw new IllegalArgumentException("consumerThreadMin must be positive");
        }
        this.consumerThreadMin = consumerThreadMin;
    }

    public int getConsumerThreadMax() {
        return consumerThreadMax;
    }

    public void setConsumerThreadMax(int consumerThreadMax) {
        if (consumerThreadMax < 1) {
            throw new IllegalArgumentException("consumerThreadMax must be positive");
        }
        this.consumerThreadMax = consumerThreadMax;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}