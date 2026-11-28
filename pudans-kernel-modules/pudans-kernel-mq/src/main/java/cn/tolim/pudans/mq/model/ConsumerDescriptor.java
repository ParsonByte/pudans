package cn.tolim.pudans.mq.model;

import java.util.Set;

public record ConsumerDescriptor(String consumerGroup, Set<String> topics) {
}