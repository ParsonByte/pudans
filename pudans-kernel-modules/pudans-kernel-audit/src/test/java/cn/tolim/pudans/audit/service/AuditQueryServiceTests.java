package cn.tolim.pudans.audit.service;

import cn.tolim.pudans.audit.dto.AuditPage;
import cn.tolim.pudans.audit.dto.AuditRecordView;
import cn.tolim.pudans.audit.entity.AuditEntity;
import cn.tolim.pudans.audit.enums.EventStatus;
import cn.tolim.pudans.audit.event.type.EventType;
import cn.tolim.pudans.audit.query.AuditQuery;
import cn.tolim.pudans.audit.repository.AuditRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditQueryServiceTests {

    private final AuditRepository auditRepository = mock(AuditRepository.class);

    private final AuditQueryService auditQueryService = new AuditQueryService(auditRepository);

    @Test
    void searchesWithFiltersAndOneBasedPagination() {
        when(auditRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    return new PageImpl<>(List.of(auditEntity()), pageable, 401);
                });
        AuditQuery query = new AuditQuery();
        query.setActorId("user-1");
        query.setEventType(EventType.LOGIN_SUCCESS.getCode());
        query.setEventStatus(EventStatus.SUCCESS.getCode());
        query.setKeyword("alice");
        query.setPage(2);
        query.setSize(200);
        query.setSortBy("createTime");
        query.setSortDirection("asc");

        AuditPage<AuditRecordView> result = auditQueryService.search(query);

        assertThat(result.items()).singleElement().satisfies(view -> {
            assertThat(view.id()).isEqualTo("record-1");
            assertThat(view.eventType()).isEqualTo(EventType.SESSION_KICKED.getCode());
            assertThat(view.eventCategory()).isEqualTo("session");
            assertThat(view.actorId()).isEqualTo("user-1");
        });
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(200);
        assertThat(result.total()).isEqualTo(401);
        assertThat(result.totalPages()).isEqualTo(3);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(200);
        assertThat(pageable.getSort().getOrderFor("createTime"))
                .isNotNull()
                .satisfies(order -> assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC));
    }

    @Test
    void appliesDefaultsAndClampsPageValues() {
        when(auditRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    return new PageImpl<>(List.of(), pageable, 0);
                });
        AuditQuery query = new AuditQuery();
        query.setPage(0);
        query.setSize(5000);
        query.setSortBy("not-a-field");
        query.setSortDirection("sideways");

        auditQueryService.search(query);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(200);
        assertThat(pageable.getSort().getOrderFor("eventTime"))
                .isNotNull()
                .satisfies(order -> assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC));
    }

    @Test
    void rejectsUnknownEventTypeAndStatus() {
        AuditQuery query = new AuditQuery();
        query.setEventType("unknown:event");
        assertThatThrownBy(() -> auditQueryService.search(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown audit event type");

        AuditQuery statusQuery = new AuditQuery();
        statusQuery.setEventStatus("unknown");
        assertThatThrownBy(() -> auditQueryService.search(statusQuery))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown audit event status");
    }

    @Test
    void rejectsInvalidTimeRange() {
        AuditQuery query = new AuditQuery();
        query.setStartTime(LocalDateTime.of(2026, 11, 27, 12, 0));
        query.setEndTime(LocalDateTime.of(2026, 11, 27, 10, 0));

        assertThatThrownBy(() -> auditQueryService.search(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start time");
    }

    @Test
    void findsRecordAndOrderedTrails() {
        when(auditRepository.findById("record-1")).thenReturn(Optional.of(auditEntity()));
        when(auditRepository.findAllByRequestIdOrderByEventTimeAsc("req-1"))
                .thenReturn(List.of(auditEntity()));
        when(auditRepository.findAllBySessionIdOrderByEventTimeAsc("sid-1"))
                .thenReturn(List.of(auditEntity()));

        assertThat(auditQueryService.findById("record-1"))
                .hasValueSatisfying(view -> assertThat(view.id()).isEqualTo("record-1"));
        assertThat(auditQueryService.findById("missing")).isEmpty();
        assertThat(auditQueryService.findById(" ")).isEmpty();
        assertThat(auditQueryService.findByRequestId("req-1")).hasSize(1);
        assertThat(auditQueryService.findByRequestId(null)).isEmpty();
        assertThat(auditQueryService.findBySessionId("sid-1")).hasSize(1);
        assertThat(auditQueryService.findBySessionId(" ")).isEmpty();
    }

    private static AuditEntity auditEntity() {
        AuditEntity entity = new AuditEntity();
        entity.setId("record-1");
        entity.setRequestId("req-1");
        entity.setSessionId("sid-1");
        entity.setActorId("user-1");
        entity.setActorType("user");
        entity.setActorAuthType("password");
        entity.setEventType(EventType.SESSION_KICKED);
        entity.setEventStatus(EventStatus.SUCCESS);
        entity.setEventContent("session kicked by administrator");
        entity.setEventParam("{\"reason\":\"admin kick\"}");
        entity.setEventResult("success");
        entity.setEventTime(LocalDateTime.of(2026, 11, 27, 10, 0));
        entity.setCreateTime(LocalDateTime.of(2026, 11, 27, 10, 1));
        return entity;
    }
}