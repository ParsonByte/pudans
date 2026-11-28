package cn.tolim.pudans.mq.endpoint;

import cn.tolim.pudans.communal.core.contract.ApiResponse;
import cn.tolim.pudans.mq.compensation.MessageCompensationService;
import cn.tolim.pudans.mq.constant.MqApiPaths;
import cn.tolim.pudans.mq.enums.CompensationStatus;
import cn.tolim.pudans.mq.enums.DeadLetterStatus;
import cn.tolim.pudans.mq.enums.MessageStatus;
import cn.tolim.pudans.mq.model.CompensationResult;
import cn.tolim.pudans.mq.model.ConsumerDescriptor;
import cn.tolim.pudans.mq.model.DeadLetterQuery;
import cn.tolim.pudans.mq.model.DeadLetterResponse;
import cn.tolim.pudans.mq.model.DeliveryResponse;
import cn.tolim.pudans.mq.model.MessageDetailResponse;
import cn.tolim.pudans.mq.model.MessagePublishRequest;
import cn.tolim.pudans.mq.model.MessagePublishResult;
import cn.tolim.pudans.mq.model.MessageQuery;
import cn.tolim.pudans.mq.model.MessageResponse;
import cn.tolim.pudans.mq.model.MessageStatsResponse;
import cn.tolim.pudans.mq.model.PageResponse;
import cn.tolim.pudans.mq.producer.MessageProducer;
import cn.tolim.pudans.mq.service.MessageCenterService;
import cn.tolim.pudans.mq.service.MessageDispatchCoordinator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@Tag(name = "消息中心")
@RequestMapping(MqApiPaths.API_PREFIX)
public class MessageCenterEndpoint {

    private final MessageProducer messageProducer;

    private final MessageCenterService messageCenterService;

    private final MessageCompensationService messageCompensationService;

    private final MessageDispatchCoordinator messageDispatchCoordinator;

    public MessageCenterEndpoint(MessageProducer messageProducer,
                                 MessageCenterService messageCenterService,
                                 MessageCompensationService messageCompensationService,
                                 MessageDispatchCoordinator messageDispatchCoordinator) {
        this.messageProducer = messageProducer;
        this.messageCenterService = messageCenterService;
        this.messageCompensationService = messageCompensationService;
        this.messageDispatchCoordinator = messageDispatchCoordinator;
    }

    @Operation(summary = "发布消息")
    @PostMapping({"/messages", "/messages/send"})
    public ApiResponse<MessagePublishResult> publish(@Valid @RequestBody MessagePublishRequest request) {
        return ApiResponse.ok(messageProducer.publish(request));
    }

    @Operation(summary = "查询消息列表")
    @GetMapping("/messages")
    public ApiResponse<PageResponse<MessageResponse>> listMessages(
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String producer,
            @RequestParam(required = false) String bizKey,
            @RequestParam(required = false) MessageStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        MessageQuery query = new MessageQuery(topic, eventType, producer, bizKey, status, keyword,
                createdFrom, createdTo, page, size);
        return ApiResponse.ok(messageCenterService.listMessages(query));
    }

    @Operation(summary = "查询消息详情")
    @GetMapping("/messages/{id}")
    public ApiResponse<MessageDetailResponse> getMessage(@PathVariable String id) {
        return ApiResponse.ok(messageCenterService.getMessage(id));
    }

    @Operation(summary = "查询投递记录")
    @GetMapping("/messages/{id}/deliveries")
    public ApiResponse<List<DeliveryResponse>> getDeliveries(@PathVariable String id) {
        return ApiResponse.ok(messageCenterService.getDeliveries(id));
    }

    @Operation(summary = "重试消息")
    @PostMapping("/messages/{id}/retry")
    public ApiResponse<MessageResponse> retryMessage(@PathVariable String id) {
        return ApiResponse.ok(messageCenterService.retryMessage(id));
    }

    @Operation(summary = "取消消息")
    @PostMapping("/messages/{id}/cancel")
    public ApiResponse<MessageResponse> cancelMessage(@PathVariable String id) {
        return ApiResponse.ok(messageCenterService.cancelMessage(id));
    }

    @Operation(summary = "查询死信列表")
    @GetMapping("/dead-letters")
    public ApiResponse<PageResponse<DeadLetterResponse>> listDeadLetters(
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) String consumerGroup,
            @RequestParam(required = false) DeadLetterStatus status,
            @RequestParam(required = false) CompensationStatus compensationStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        DeadLetterQuery query = new DeadLetterQuery(topic, messageId, consumerGroup, status,
                compensationStatus, page, size);
        return ApiResponse.ok(messageCenterService.listDeadLetters(query));
    }

    @Operation(summary = "查询死信详情")
    @GetMapping("/dead-letters/{id}")
    public ApiResponse<DeadLetterResponse> getDeadLetter(@PathVariable String id) {
        return ApiResponse.ok(messageCenterService.getDeadLetter(id));
    }

    @Operation(summary = "重放死信")
    @PostMapping("/dead-letters/{id}/replay")
    public ApiResponse<DeadLetterResponse> replayDeadLetter(@PathVariable String id) {
        return ApiResponse.ok(messageCenterService.replayDeadLetter(id));
    }

    @Operation(summary = "丢弃死信")
    @PostMapping("/dead-letters/{id}/discard")
    public ApiResponse<DeadLetterResponse> discardDeadLetter(@PathVariable String id) {
        return ApiResponse.ok(messageCenterService.discardDeadLetter(id));
    }

    @Operation(summary = "补偿死信")
    @PostMapping("/dead-letters/{id}/compensate")
    public ApiResponse<CompensationResult> compensateDeadLetter(@PathVariable String id) {
        return ApiResponse.ok(messageCompensationService.compensate(id));
    }

    @Operation(summary = "查询消息统计")
    @GetMapping("/stats")
    public ApiResponse<MessageStatsResponse> stats() {
        return ApiResponse.ok(messageCenterService.stats());
    }

    @Operation(summary = "查询消费者")
    @GetMapping("/consumers")
    public ApiResponse<List<ConsumerDescriptor>> consumers() {
        return ApiResponse.ok(messageCenterService.consumers());
    }

    @Operation(summary = "分发到期消息")
    @PostMapping("/dispatch")
    public ApiResponse<Integer> dispatchDue() {
        return ApiResponse.ok(messageDispatchCoordinator.dispatchDue());
    }
}