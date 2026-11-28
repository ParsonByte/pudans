package cn.tolim.pudans.iam.communal.repository.app.impl;

import cn.tolim.pudans.iam.communal.entity.app.po.AppAccessPolicyPO;
import cn.tolim.pudans.iam.communal.entity.app.query.AppAccessPolicyQueryParam;
import cn.tolim.pudans.iam.communal.enums.app.AppPolicySubjectType;
import cn.tolim.pudans.iam.infra.exception.PudansException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AppAccessPolicyRepositoryCustomizedImplTests {

    @SuppressWarnings("unchecked")
    private static TypedQuery<AppAccessPolicyPO> listQuery() {
        return mock(TypedQuery.class);
    }

    @SuppressWarnings("unchecked")
    private static TypedQuery<Long> countQuery() {
        return mock(TypedQuery.class);
    }

    @Test
    void shouldFilterAndPageApplicationAccessPolicies() {
        EntityManager entityManager = mock(EntityManager.class);
        TypedQuery<AppAccessPolicyPO> listQuery = listQuery();
        TypedQuery<Long> countQuery = countQuery();
        AppAccessPolicyPO policy = mock(AppAccessPolicyPO.class);
        when(entityManager.createQuery(anyString(), eq(AppAccessPolicyPO.class))).thenReturn(listQuery);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(listQuery.getResultList()).thenReturn(List.of(policy));
        when(countQuery.getSingleResult()).thenReturn(1L);
        AppAccessPolicyRepositoryCustomizedImpl repository =
                new AppAccessPolicyRepositoryCustomizedImpl(entityManager);
        AppAccessPolicyQueryParam query = new AppAccessPolicyQueryParam();
        query.setAppId("app-1");
        query.setSubjectId("subject-1");
        query.setSubjectType(AppPolicySubjectType.USER);
        query.setSubjectName("ali");
        query.setAppName("Order");
        Pageable pageable = PageRequest.of(0, 10);

        Page<AppAccessPolicyPO> page = repository.getAppPolicyList(query, pageable);

        assertEquals(1, page.getTotalElements());
        assertEquals(1, page.getContent().size());
        assertSame(policy, page.getContent().getFirst());
        verify(listQuery).setParameter("subjectType", AppPolicySubjectType.USER);
        verify(listQuery).setParameter("subjectName", "%ali%");
        verify(listQuery).setParameter("subjectId", "subject-1");
        verify(listQuery).setParameter("appId", "app-1");
        verify(listQuery).setParameter("appName", "%Order%");
        verify(listQuery).setFirstResult(0);
        verify(listQuery).setMaxResults(10);
        verify(countQuery).setParameter("subjectType", AppPolicySubjectType.USER);
    }

    @Test
    void shouldRequireSubjectOrApplication() {
        EntityManager entityManager = mock(EntityManager.class);
        AppAccessPolicyRepositoryCustomizedImpl repository =
                new AppAccessPolicyRepositoryCustomizedImpl(entityManager);
        AppAccessPolicyQueryParam query = new AppAccessPolicyQueryParam();

        PudansException exception = assertThrows(PudansException.class,
                () -> repository.getAppPolicyList(query, PageRequest.of(0, 10)));

        assertEquals("主体ID不能为空", exception.getMessage());
        verifyNoInteractions(entityManager);
    }

    @Test
    void shouldAllowQueriesByApplicationOnly() {
        EntityManager entityManager = mock(EntityManager.class);
        TypedQuery<AppAccessPolicyPO> listQuery = listQuery();
        TypedQuery<Long> countQuery = countQuery();
        when(entityManager.createQuery(anyString(), eq(AppAccessPolicyPO.class))).thenReturn(listQuery);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(listQuery.getResultList()).thenReturn(List.of());
        when(countQuery.getSingleResult()).thenReturn(0L);
        AppAccessPolicyRepositoryCustomizedImpl repository =
                new AppAccessPolicyRepositoryCustomizedImpl(entityManager);
        AppAccessPolicyQueryParam query = new AppAccessPolicyQueryParam();
        query.setAppId("app-1");

        Page<AppAccessPolicyPO> page = repository.getAppPolicyList(query, PageRequest.of(2, 5));

        assertEquals(0, page.getTotalElements());
        verify(listQuery).setParameter("appId", "app-1");
        verify(listQuery).setFirstResult(10);
        verify(listQuery).setMaxResults(5);
        verify(listQuery, never()).setParameter(eq("subjectId"), anyString());
    }
}