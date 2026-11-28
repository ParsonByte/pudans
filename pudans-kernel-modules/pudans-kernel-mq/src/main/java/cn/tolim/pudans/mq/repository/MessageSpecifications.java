package cn.tolim.pudans.mq.repository;

import cn.tolim.pudans.mq.entity.DeadLetterEntity;
import cn.tolim.pudans.mq.entity.MessageEntity;
import cn.tolim.pudans.mq.model.DeadLetterQuery;
import cn.tolim.pudans.mq.model.MessageQuery;
import cn.tolim.pudans.mq.util.MessageTexts;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class MessageSpecifications {

    private MessageSpecifications() {
    }

    public static Specification<MessageEntity> messages(MessageQuery query) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!MessageTexts.isBlank(query.topic())) {
                predicates.add(builder.equal(root.get("topic"), query.topic().trim()));
            }
            if (!MessageTexts.isBlank(query.eventType())) {
                predicates.add(builder.equal(root.get("eventType"), query.eventType().trim()));
            }
            if (!MessageTexts.isBlank(query.producer())) {
                predicates.add(builder.equal(root.get("producer"), query.producer().trim()));
            }
            if (!MessageTexts.isBlank(query.bizKey())) {
                predicates.add(builder.equal(root.get("bizKey"), query.bizKey().trim()));
            }
            if (query.status() != null) {
                predicates.add(builder.equal(root.get("status"), query.status()));
            }
            if (!MessageTexts.isBlank(query.keyword())) {
                String pattern = "%" + query.keyword().trim() + "%";
                predicates.add(builder.or(
                        builder.like(root.get("topic"), pattern),
                        builder.like(root.get("bizKey"), pattern),
                        builder.like(root.get("producer"), pattern),
                        builder.like(root.get("eventType"), pattern)));
            }
            if (query.createdFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createTime"), query.createdFrom()));
            }
            if (query.createdTo() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createTime"), query.createdTo()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<DeadLetterEntity> deadLetters(DeadLetterQuery query) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!MessageTexts.isBlank(query.topic())) {
                predicates.add(builder.equal(root.get("topic"), query.topic().trim()));
            }
            if (!MessageTexts.isBlank(query.messageId())) {
                predicates.add(builder.equal(root.get("messageId"), query.messageId().trim()));
            }
            if (!MessageTexts.isBlank(query.consumerGroup())) {
                predicates.add(builder.equal(root.get("consumerGroup"), query.consumerGroup().trim()));
            }
            if (query.status() != null) {
                predicates.add(builder.equal(root.get("status"), query.status()));
            }
            if (query.compensationStatus() != null) {
                predicates.add(builder.equal(root.get("compensationStatus"), query.compensationStatus()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}