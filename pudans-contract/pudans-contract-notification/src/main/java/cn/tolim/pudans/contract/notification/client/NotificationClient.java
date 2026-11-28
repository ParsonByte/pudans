package cn.tolim.pudans.contract.notification.client;

import cn.tolim.pudans.contract.notification.NotificationContractConstants;
import cn.tolim.pudans.contract.notification.NotificationApiPaths;
import cn.tolim.pudans.contract.notification.dto.DeadLetterQueryRequest;
import cn.tolim.pudans.contract.notification.dto.DeadLetterResponse;
import cn.tolim.pudans.contract.notification.dto.NotificationApiResult;
import cn.tolim.pudans.contract.notification.dto.NotificationPageResponse;
import cn.tolim.pudans.contract.notification.dto.NotificationRecordQueryRequest;
import cn.tolim.pudans.contract.notification.dto.NotificationSendResult;
import cn.tolim.pudans.contract.notification.dto.NotificationStatsResponse;
import cn.tolim.pudans.contract.notification.dto.ProviderQueryRequest;
import cn.tolim.pudans.contract.notification.dto.ProviderRequest;
import cn.tolim.pudans.contract.notification.dto.ProviderResponse;
import cn.tolim.pudans.contract.notification.dto.ProviderTestRequest;
import cn.tolim.pudans.contract.notification.dto.SendNotificationRequest;
import cn.tolim.pudans.contract.notification.dto.SendNotificationResponse;
import cn.tolim.pudans.contract.notification.dto.TemplateQueryRequest;
import cn.tolim.pudans.contract.notification.dto.TemplateRequest;
import cn.tolim.pudans.contract.notification.dto.TemplateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

// Notification service contract
@FeignClient(name = NotificationContractConstants.SERVICE_NAME, contextId = "notificationClient",
        path = NotificationApiPaths.BASE)
public interface NotificationClient {

    // Send one notification
    // Parameter request: SendNotificationRequest
    // Returns: NotificationApiResult<SendNotificationResponse>
    @PostMapping(NotificationApiPaths.SEND_SUFFIX)
    NotificationApiResult<SendNotificationResponse> send(@RequestBody SendNotificationRequest request);

    // Send a batch of notifications
    // Parameter requests: List<SendNotificationRequest>
    // Returns: NotificationApiResult<List<SendNotificationResponse>>
    @PostMapping(NotificationApiPaths.SEND_BATCH_SUFFIX)
    NotificationApiResult<List<SendNotificationResponse>> sendBatch(
            @RequestBody List<SendNotificationRequest> requests);

    // Get one send record
    // Parameter id: String
    // Returns: NotificationApiResult<SendNotificationResponse>
    @GetMapping(NotificationApiPaths.RECORD_DETAIL_SUFFIX)
    NotificationApiResult<SendNotificationResponse> getRecord(@PathVariable("id") String id);

    // Search send records
    // Parameter query: NotificationRecordQueryRequest
    // Returns: NotificationApiResult<NotificationPageResponse<SendNotificationResponse>>
    @GetMapping(NotificationApiPaths.RECORDS_SUFFIX)
    NotificationApiResult<NotificationPageResponse<SendNotificationResponse>> searchRecords(
            @SpringQueryMap NotificationRecordQueryRequest query);

    // Retry a send record
    // Parameter id: String
    // Returns: NotificationApiResult<SendNotificationResponse>
    @PostMapping(NotificationApiPaths.RECORD_RETRY_SUFFIX)
    NotificationApiResult<SendNotificationResponse> retry(@PathVariable("id") String id);

    // Search dead letters
    // Parameter query: DeadLetterQueryRequest
    // Returns: NotificationApiResult<NotificationPageResponse<DeadLetterResponse>>
    @GetMapping(NotificationApiPaths.DEAD_LETTERS_SUFFIX)
    NotificationApiResult<NotificationPageResponse<DeadLetterResponse>> searchDeadLetters(
            @SpringQueryMap DeadLetterQueryRequest query);

    // Get one dead letter
    // Parameter id: String
    // Returns: NotificationApiResult<DeadLetterResponse>
    @GetMapping(NotificationApiPaths.DEAD_LETTER_DETAIL_SUFFIX)
    NotificationApiResult<DeadLetterResponse> getDeadLetter(@PathVariable("id") String id);

    // Replay one dead letter
    // Parameter id: String
    // Returns: NotificationApiResult<SendNotificationResponse>
    @PostMapping(NotificationApiPaths.DEAD_LETTER_REPLAY_SUFFIX)
    NotificationApiResult<SendNotificationResponse> replayDeadLetter(@PathVariable("id") String id);

    // Get send statistics
    // Returns: NotificationApiResult<NotificationStatsResponse>
    @GetMapping(NotificationApiPaths.STATS_SUFFIX)
    NotificationApiResult<NotificationStatsResponse> getStats();

    // Search providers
    // Parameter query: ProviderQueryRequest
    // Returns: NotificationApiResult<NotificationPageResponse<ProviderResponse>>
    @GetMapping(NotificationApiPaths.PROVIDERS_SUFFIX)
    NotificationApiResult<NotificationPageResponse<ProviderResponse>> searchProviders(
            @SpringQueryMap ProviderQueryRequest query);

    // Get one provider
    // Parameter id: String
    // Returns: NotificationApiResult<ProviderResponse>
    @GetMapping(NotificationApiPaths.PROVIDER_DETAIL_SUFFIX)
    NotificationApiResult<ProviderResponse> getProvider(@PathVariable("id") String id);

    // Create a provider
    // Parameter request: ProviderRequest
    // Returns: NotificationApiResult<ProviderResponse>
    @PostMapping(NotificationApiPaths.PROVIDERS_SUFFIX)
    NotificationApiResult<ProviderResponse> createProvider(@RequestBody ProviderRequest request);

    // Update a provider
    // Parameter id: String
    // Parameter request: ProviderRequest
    // Returns: NotificationApiResult<ProviderResponse>
    @PutMapping(NotificationApiPaths.PROVIDER_DETAIL_SUFFIX)
    NotificationApiResult<ProviderResponse> updateProvider(@PathVariable("id") String id,
                                                          @RequestBody ProviderRequest request);

    // Delete a provider
    // Parameter id: String
    // Returns: NotificationApiResult<Void>
    @DeleteMapping(NotificationApiPaths.PROVIDER_DETAIL_SUFFIX)
    NotificationApiResult<Void> deleteProvider(@PathVariable("id") String id);

    // Test a provider configuration
    // Parameter id: String
    // Parameter request: ProviderTestRequest
    // Returns: NotificationApiResult<NotificationSendResult>
    @PostMapping(NotificationApiPaths.PROVIDER_TEST_SUFFIX)
    NotificationApiResult<NotificationSendResult> testProvider(@PathVariable("id") String id,
                                                              @RequestBody ProviderTestRequest request);

    // Search templates
    // Parameter query: TemplateQueryRequest
    // Returns: NotificationApiResult<NotificationPageResponse<TemplateResponse>>
    @GetMapping(NotificationApiPaths.TEMPLATES_SUFFIX)
    NotificationApiResult<NotificationPageResponse<TemplateResponse>> searchTemplates(
            @SpringQueryMap TemplateQueryRequest query);

    // Get one template
    // Parameter id: String
    // Returns: NotificationApiResult<TemplateResponse>
    @GetMapping(NotificationApiPaths.TEMPLATE_DETAIL_SUFFIX)
    NotificationApiResult<TemplateResponse> getTemplate(@PathVariable("id") String id);

    // Create a template
    // Parameter request: TemplateRequest
    // Returns: NotificationApiResult<TemplateResponse>
    @PostMapping(NotificationApiPaths.TEMPLATES_SUFFIX)
    NotificationApiResult<TemplateResponse> createTemplate(@RequestBody TemplateRequest request);

    // Update a template
    // Parameter id: String
    // Parameter request: TemplateRequest
    // Returns: NotificationApiResult<TemplateResponse>
    @PutMapping(NotificationApiPaths.TEMPLATE_DETAIL_SUFFIX)
    NotificationApiResult<TemplateResponse> updateTemplate(@PathVariable("id") String id,
                                                          @RequestBody TemplateRequest request);

    // Delete a template
    // Parameter id: String
    // Returns: NotificationApiResult<Void>
    @DeleteMapping(NotificationApiPaths.TEMPLATE_DETAIL_SUFFIX)
    NotificationApiResult<Void> deleteTemplate(@PathVariable("id") String id);
}