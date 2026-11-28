package cn.tolim.pudans.iam.communal.repository.account.impl;

import cn.tolim.pudans.iam.communal.entity.account.UserDetailEntity;
import cn.tolim.pudans.iam.communal.enums.account.UserIdType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDetailRepositoryCustomizedImplTests {

    private static UserDetailEntity entity(String id, String userId) {
        UserDetailEntity entity = new UserDetailEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setIdType(UserIdType.IDENTITY_CARD);
        entity.setIdCard("110101199001011234");
        entity.setWebsite("https://example.com");
        entity.setAddress("Jinan");
        entity.setCreateBy("pudans");
        entity.setCreateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        entity.setUpdateBy("pudans");
        entity.setUpdateTime(LocalDateTime.of(2026, 1, 2, 11, 0));
        entity.setRemark("remark");
        return entity;
    }

    @Test
    void shouldBatchInsertUserDetailsWithTypedColumns() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenReturn(new int[]{1});
        UserDetailRepositoryCustomizedImpl repository =
                new UserDetailRepositoryCustomizedImpl(jdbcTemplate);
        UserDetailEntity entity = entity("detail-1", "user-1");

        repository.batchSave(List.of(entity));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor =
                ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), setterCaptor.capture());
        assertTrue(sqlCaptor.getValue().contains("INSERT INTO pudans_user_detail"));
        BatchPreparedStatementSetter setter = setterCaptor.getValue();
        assertEquals(1, setter.getBatchSize());
        PreparedStatement statement = mock(PreparedStatement.class);
        setter.setValues(statement, 0);
        verify(statement).setString(1, "detail-1");
        verify(statement).setString(2, "user-1");
        verify(statement).setString(3, UserIdType.IDENTITY_CARD.getCode());
        verify(statement).setString(4, "110101199001011234");
        verify(statement).setString(5, "https://example.com");
        verify(statement).setString(6, "Jinan");
        verify(statement).setString(11, "remark");
        verify(statement).setBoolean(12, false);
    }

    @Test
    void shouldBatchUpdateUserDetailsByIdentifier() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenReturn(new int[]{1});
        UserDetailRepositoryCustomizedImpl repository =
                new UserDetailRepositoryCustomizedImpl(jdbcTemplate);
        UserDetailEntity entity = entity("detail-1", "user-1");

        repository.batchUpdate(new ArrayList<>(List.of(entity)));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor =
                ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), setterCaptor.capture());
        assertTrue(sqlCaptor.getValue().contains("UPDATE  pudans_user_detail"));
        assertTrue(sqlCaptor.getValue().endsWith("WHERE id_=?"));
        PreparedStatement statement = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(statement, 0);
        verify(statement).setString(1, "user-1");
        verify(statement).setString(2, UserIdType.IDENTITY_CARD.getCode());
        verify(statement).setString(11, "detail-1");
    }

    @Test
    void shouldWriteNullForMissingDocumentType() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenReturn(new int[]{1});
        UserDetailRepositoryCustomizedImpl repository =
                new UserDetailRepositoryCustomizedImpl(jdbcTemplate);
        UserDetailEntity entity = entity("detail-1", "user-1");
        entity.setIdType(null);

        repository.batchSave(List.of(entity));

        ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor =
                ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(anyString(), setterCaptor.capture());
        PreparedStatement statement = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(statement, 0);
        verify(statement).setString(3, (String) null);
    }
}