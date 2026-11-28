package cn.tolim.pudans.contract.mq.client;

import cn.tolim.pudans.contract.mq.MqApiPaths;
import cn.tolim.pudans.contract.mq.MqContractConstants;
import cn.tolim.pudans.contract.mq.dto.CompensationResult;
import cn.tolim.pudans.contract.mq.dto.ConsumerDescriptor;
import cn.tolim.pudans.contract.mq.dto.DeadLetterResponse;
import cn.tolim.pudans.contract.mq.dto.DeliveryResponse;
import cn.tolim.pudans.contract.mq.dto.MessageDetailResponse;
import cn.tolim.pudans.contract.mq.dto.MessagePublishRequest;
import cn.tolim.pudans.contract.mq.dto.MessagePublishResult;
import cn.tolim.pudans.contract.mq.dto.MessageResponse;
import cn.tolim.pudans.contract.mq.dto.MessageStatsResponse;
import cn.tolim.pudans.contract.mq.dto.MqApiResult;
import cn.tolim.pudans.contract.mq.dto.PageResponse;
import cn.tolim.pudans.contract.mq.enums.CompensationStatus;
import cn.tolim.pudans.contract.mq.enums.DeadLetterStatus;
import cn.tolim.pudans.contract.mq.enums.MessageStatus;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

// Message center service contract
@FeignClient(name = MqContractConstants.SERVICE_NAME, contextId = "messageCenterClient",
        path = MqApiPaths.BASE)
public interface MessageCenterClient {

    // Publish one message
    // Parameter request: MessagePublishRequest
    // Returns: MqApiResult<MessagePublishResult>
    @PostMapping(MqApiPaths.MESSAGES_SUFFIX)
    MqApiResult<MessagePublishResult> publish(@RequestBody MessagePublishRequest request);

    // List messages with filters and paging
    // Returns: MqApiResult<PageResponse<MessageResponse>>
    @GetMapping(MqApiPaths.MESSAGES_SUFFIX)
    MqApiResult<PageResponse<MessageResponse>> listMessages(
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "eventType", required = false) String eventType,
            @RequestParam(value = "producer", required = false) String producer,
            @RequestParam(value = "bizKey", required = false) String bizKey,
            @RequestParam(value = "status", required = false) MessageStatus status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(value = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(value = "page", defaultValue = MqContractConstants.DEFAULT_PAGE_NUMBER)
            int page,
            @RequestParam(value = "size", defaultValue = MqContractConstants.DEFAULT_PAGE_SIZE)
            int size);

    // Load one message with deliveries and dead letters
    // Parameter id: String
    // Returns: MqApiResult<MessageDetailResponse>
    @GetMapping(MqApiPaths.MESSAGE_DETAIL_SUFFIX)
    MqApiResult<MessageDetailResponse> getMessage(@PathVariable("id") String id);

    // List delivery attempts of one message
    // Parameter id: String
    // Returns: MqApiResult<List<DeliveryResponse>>
    @GetMapping(MqApiPaths.MESSAGE_DELIVERIES_SUFFIX)
    MqApiResult<List<DeliveryResponse>> getDeliveries(@PathVariable("id") String id);

    // Requeue one message
    // Parameter id: String
    // Returns: MqApiResult<MessageResponse>
    @PostMapping(MqApiPaths.MESSAGE_RETRY_SUFFIX)
    MqApiResult<MessageResponse> retryMessage(@PathVariable("id") String id);

    // Cancel one message
    // Parameter id: String
    // Returns: MqApiResult<MessageResponse>
    @PostMapping(MqApiPaths.MESSAGE_CANCEL_SUFFIX)
    MqApiResult<MessageResponse> cancelMessage(@PathVariable("id") String id);

    // List dead letters with filters and paging
    // Returns: MqApiResult<PageResponse<DeadLetterResponse>>
    @GetMapping(MqApiPaths.DEAD_LETTERS_SUFFIX)
    MqApiResult<PageResponse<DeadLetterResponse>> listDeadLetters(
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "messageId", required = false) String messageId,
            @RequestParam(value = "consumerGroup", required = false) String consumerGroup,
            @RequestParam(value = "status", required = false) DeadLetterStatus status,
            @RequestParam(value = "compensationStatus", required = false)
            CompensationStatus compensationStatus,
            @RequestParam(value = "page", defaultValue = MqContractConstants.DEFAULT_PAGE_NUMBER)
            int page,
            @RequestParam(value = "size", defaultValue = MqContractConstants.DEFAULT_PAGE_SIZE)
            int size);

    // Load one dead letter
    // Parameter id: String
    // Returns: MqApiResult<DeadLetterResponse>
    @GetMapping(MqApiPaths.DEAD_LETTER_DETAIL_SUFFIX)
    MqApiResult<DeadLetterResponse> getDeadLetter(@PathVariable("id") String id);

    // Replay one dead letter
    // Parameter id: String
    // Returns: MqApiResult<DeadLetterResponse>
    @PostMapping(MqApiPaths.DEAD_LETTER_REPLAY_SUFFIX)
    MqApiResult<DeadLetterResponse> replayDeadLetter(@PathVariable("id") String id);

    // Discard one dead letter
    // Parameter id: String
    // Returns: MqApiResult<DeadLetterResponse>
    @PostMapping(MqApiPaths.DEAD_LETTER_DISCARD_SUFFIX)
    MqApiResult<DeadLetterResponse> discardDeadLetter(@PathVariable("id") String id);

    // Compensate one dead letter
    // Parameter id: String
    // Returns: MqApiResult<CompensationResult>
    @PostMapping(MqApiPaths.DEAD_LETTER_COMPENSATE_SUFFIX)
    MqApiResult<CompensationResult> compensateDeadLetter(@PathVariable("id") String id);

    // Message and delivery counters
    // Returns: MqApiResult<MessageStatsResponse>
    @GetMapping(MqApiPaths.STATS_SUFFIX)
    MqApiResult<MessageStatsResponse> stats();

    // Registered consumer groups
    // Returns: MqApiResult<List<ConsumerDescriptor>>
    @GetMapping(MqApiPaths.CONSUMERS_SUFFIX)
    MqApiResult<List<ConsumerDescriptor>> consumers();

    // Trigger one dispatch round
    // Returns: MqApiResult<Integer>
    @PostMapping(MqApiPaths.DISPATCH_SUFFIX)
    MqApiResult<Integer> dispatchDue();
}