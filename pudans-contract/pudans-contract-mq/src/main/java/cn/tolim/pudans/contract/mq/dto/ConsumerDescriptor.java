package cn.tolim.pudans.contract.mq.dto;

import java.util.Set;

// Registered consumer group and its topics
public record ConsumerDescriptor(String consumerGroup, Set<String> topics) {
}