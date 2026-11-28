package cn.tolim.pudans.notification.controller;

import cn.tolim.pudans.iam.infra.result.ApiRestResult;
import cn.tolim.pudans.notification.dto.DeadLetterResponse;
import cn.tolim.pudans.notification.dto.NotificationStatsResponse;
import cn.tolim.pudans.notification.dto.SendNotificationRequest;
import cn.tolim.pudans.notification.dto.SendNotificationResponse;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.SendStatus;
import cn.tolim.pudans.notification.service.NotificationQueryService;
import cn.tolim.pudans.notification.service.NotificationSendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Validated
@RestController
@Tag(name = "通知发送")
@RequestMapping(value = NotificationApiPaths.BASE, produces = MediaType.APPLICATION_JSON_VALUE)
public class NotificationSendEndpoint {

    private final NotificationSendService sendService;
    private final NotificationQueryService queryService;

    public NotificationSendEndpoint(NotificationSendService sendService,
                                    NotificationQueryService queryService) {
        this.sendService = sendService;
        this.queryService = queryService;
    }

    @Operation(summary = "发送通知")
    @PostMapping("/send")
    public ApiRestResult<SendNotificationResponse> send(
            @Valid @RequestBody SendNotificationRequest request) {
        return ApiRestResult.ok(sendService.send(request));
    }

    @Operation(summary = "批量发送通知")
    @PostMapping("/send/batch")
    public ApiRestResult<List<SendNotificationResponse>> sendBatch(
            @RequestBody List<@Valid SendNotificationRequest> requests) {
        List<SendNotificationResponse> responses = new ArrayList<>();
        for (SendNotificationRequest request : requests) {
            responses.add(sendService.send(request));
        }
        return ApiRestResult.ok(responses);
    }

    @Operation(summary = "查询发送记录详情")
    @GetMapping("/records/{id}")
    public ApiRestResult<SendNotificationResponse> getRecord(@PathVariable String id) {
        return ApiRestResult.ok(queryService.getSendRecord(id));
    }

    @Operation(summary = "查询发送记录")
    @GetMapping("/records")
    public ApiRestResult<Page<SendNotificationResponse>> listRecords(
            @RequestParam(required = false) SendStatus status,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) String providerCode,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String templateCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), boundedSize(size),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return ApiRestResult.ok(queryService.searchSendRecords(status, channel, providerCode,
                recipient, requestId, templateCode, pageable));
    }

    @Operation(summary = "重试发送")
    @PostMapping("/records/{id}/retry")
    public ApiRestResult<SendNotificationResponse> retry(@PathVariable String id) {
        return ApiRestResult.ok(sendService.retry(id));
    }

    @Operation(summary = "查询死信列表")
    @GetMapping("/dead-letters")
    public ApiRestResult<Page<DeadLetterResponse>> listDeadLetters(
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) Boolean replayed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), boundedSize(size),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return ApiRestResult.ok(queryService.searchDeadLetters(channel, replayed, pageable));
    }

    @Operation(summary = "查询死信详情")
    @GetMapping("/dead-letters/{id}")
    public ApiRestResult<DeadLetterResponse> getDeadLetter(@PathVariable String id) {
        return ApiRestResult.ok(queryService.getDeadLetter(id));
    }

    @Operation(summary = "重放死信")
    @PostMapping("/dead-letters/{id}/replay")
    public ApiRestResult<SendNotificationResponse> replay(@PathVariable String id) {
        return ApiRestResult.ok(sendService.replayDeadLetter(id));
    }

    @Operation(summary = "查询发送统计")
    @GetMapping("/stats")
    public ApiRestResult<NotificationStatsResponse> stats() {
        return ApiRestResult.ok(queryService.stats());
    }

    private int boundedSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 200);
    }
}