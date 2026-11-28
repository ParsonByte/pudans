package cn.tolim.pudans.communal.mybatis.jdbc;

import cn.tolim.pudans.communal.mybatis.page.PageQuery;
import cn.tolim.pudans.communal.mybatis.page.PageResult;
import cn.tolim.pudans.communal.mybatis.sql.PostgresDialect;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Executes paged PostgreSQL queries with a separate count statement.
public class JdbcPageExecutor {

    private final JdbcClient jdbcClient;
    private final int queryTimeoutSeconds;

    public JdbcPageExecutor(JdbcClient jdbcClient) {
        this(jdbcClient, 0);
    }

    public JdbcPageExecutor(JdbcClient jdbcClient, int queryTimeoutSeconds) {
        if (jdbcClient == null) {
            throw new IllegalArgumentException("jdbcClient must not be null");
        }
        if (queryTimeoutSeconds < 0) {
            throw new IllegalArgumentException("queryTimeoutSeconds must not be negative");
        }
        this.jdbcClient = jdbcClient;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    public <T> PageResult<T> findPage(String selectSql, Map<String, ?> parameters, PageQuery query, RowMapper<T> rowMapper) {
        return findPage(selectSql, PostgresDialect.countSql(selectSql), parameters, query, rowMapper);
    }

    public <T> PageResult<T> findPage(String selectSql, String countSql, Map<String, ?> parameters,
                                      PageQuery query, RowMapper<T> rowMapper) {
        if (rowMapper == null) {
            throw new IllegalArgumentException("rowMapper must not be null");
        }
        Map<String, Object> resolvedParameters = copyParameters(parameters);
        List<T> items = withTimeout(jdbcClient.sql(PostgresDialect.pageSql(selectSql, query)))
                .params(resolvedParameters)
                .query(rowMapper)
                .list();
        long total = count(countSql, resolvedParameters);
        return PageResult.of(items, query, total);
    }

    public long count(String countSql, Map<String, ?> parameters) {
        Map<String, Object> resolvedParameters = copyParameters(parameters);
        Long total = withTimeout(jdbcClient.sql(countSql))
                .params(resolvedParameters)
                .query(Long.class)
                .single();
        return total == null ? 0L : total;
    }

    public JdbcClient jdbcClient() {
        return jdbcClient;
    }

    // Adds extra parameters without mutating the caller supplied map.
    public static Map<String, Object> parameters(Map<String, ?> base, String name, Object value) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (base != null) {
            parameters.putAll(base);
        }
        parameters.put(name, value);
        return parameters;
    }

    private static Map<String, Object> copyParameters(Map<String, ?> parameters) {
        Map<String, Object> resolvedParameters = new LinkedHashMap<>();
        if (parameters != null) {
            resolvedParameters.putAll(parameters);
        }
        return resolvedParameters;
    }

    private JdbcClient.StatementSpec withTimeout(JdbcClient.StatementSpec statementSpec) {
        return queryTimeoutSeconds > 0 ? statementSpec.withQueryTimeout(queryTimeoutSeconds) : statementSpec;
    }
}