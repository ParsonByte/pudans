package cn.tolim.pudans.audit.endpoint;

import cn.tolim.pudans.audit.constant.AuditApiPaths;
import cn.tolim.pudans.audit.dto.AuditPage;
import cn.tolim.pudans.audit.dto.AuditRecordView;
import cn.tolim.pudans.audit.event.type.EventType;
import cn.tolim.pudans.audit.query.AuditQuery;
import cn.tolim.pudans.audit.service.AuditQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditRecordEndpointTests {

    private final AuditQueryService auditQueryService = mock(AuditQueryService.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AuditRecordEndpoint(auditQueryService)).build();

    @Test
    void searchesWithQueryParametersAndPagination() throws Exception {
        AuditRecordView view = new AuditRecordView("record-1", "req-1", "sid-1", "user-1", "user",
                "password", EventType.SESSION_KICKED.getCode(), EventType.SESSION_KICKED.getDesc(),
                "session", "success", "成功", "kicked", null, "ok", null, null, null, null, null);
        when(auditQueryService.search(any(AuditQuery.class)))
                .thenReturn(new AuditPage<>(List.of(view), 2, 50, 101, 3));

        mockMvc.perform(get(AuditApiPaths.AUDIT + AuditApiPaths.RECORDS)
                        .param("eventType", EventType.SESSION_KICKED.getCode())
                        .param("category", "session")
                        .param("page", "2")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.items[0].id").value("record-1"))
                .andExpect(jsonPath("$.result.page").value(2))
                .andExpect(jsonPath("$.result.total").value(101));

        ArgumentCaptor<AuditQuery> queryCaptor = ArgumentCaptor.forClass(AuditQuery.class);
        verify(auditQueryService).search(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getEventType())
                .isEqualTo(EventType.SESSION_KICKED.getCode());
        assertThat(queryCaptor.getValue().getCategory()).isEqualTo("session");
        assertThat(queryCaptor.getValue().getPage()).isEqualTo(2);
        assertThat(queryCaptor.getValue().getSize()).isEqualTo(50);
    }

    @Test
    void returnsBadRequestResultForUnknownFilter() throws Exception {
        when(auditQueryService.search(any(AuditQuery.class)))
                .thenThrow(new IllegalArgumentException("Unknown audit event type: unknown"));

        mockMvc.perform(get(AuditApiPaths.AUDIT + AuditApiPaths.RECORDS)
                        .param("eventType", "unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value("400"))
                .andExpect(jsonPath("$.message").value("Unknown audit event type: unknown"));
    }

    @Test
    void returnsNotFoundResultForMissingRecord() throws Exception {
        when(auditQueryService.findById("missing-1")).thenReturn(Optional.empty());

        mockMvc.perform(get(AuditApiPaths.AUDIT + "/records/missing-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value("404"));
    }

    @Test
    void listsAllEventTypesForFilterMetadata() throws Exception {
        mockMvc.perform(get(AuditApiPaths.AUDIT + AuditApiPaths.EVENT_TYPES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result").isArray())
                .andExpect(jsonPath("$.result[0].code").isNotEmpty())
                .andExpect(jsonPath("$.result[0].category").isNotEmpty());
    }
}