package cn.tolim.pudans.communal.mybatis.jdbc;

import cn.tolim.pudans.communal.mybatis.page.PageQuery;
import cn.tolim.pudans.communal.mybatis.page.PageResult;
import cn.tolim.pudans.communal.mybatis.sql.PostgresDialect;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// Small JDBC repository base with explicit SQL; subclasses provide statements and parameter mapping.
public abstract class AbstractJdbcRepository<T, ID> {

    private final JdbcClient jdbcClient;
    private final JdbcPageExecutor pageExecutor;

    protected AbstractJdbcRepository(JdbcClient jdbcClient) {
        if (jdbcClient == null) {
            throw new IllegalArgumentException("jdbcClient must not be null");
        }
        this.jdbcClient = jdbcClient;
        this.pageExecutor = new JdbcPageExecutor(jdbcClient);
    }

    protected abstract String tableName();

    protected abstract String idColumn();

    protected abstract RowMapper<T> rowMapper();

    protected abstract ID idOf(T entity);

    protected abstract String insertSql();

    protected abstract String updateSql();

    protected abstract MapSqlParameterSource insertParameters(T entity);

    protected abstract MapSqlParameterSource updateParameters(T entity);

    // Columns used by generated select statements; override for projections.
    protected String selectColumns() {
        return "*";
    }

    protected JdbcClient jdbcClient() {
        return jdbcClient;
    }

    protected JdbcPageExecutor pageExecutor() {
        return pageExecutor;
    }

    protected String tableReference() {
        return PostgresDialect.quoteIdentifier(tableName());
    }

    protected String idReference() {
        return PostgresDialect.quoteIdentifier(idColumn());
    }

    protected String idParameterName() {
        return "id";
    }

    protected String baseSelectSql() {
        return "SELECT " + selectColumns() + " FROM " + tableReference();
    }

    public Optional<T> findById(ID id) {
        requireId(id);
        return jdbcClient.sql(baseSelectSql() + " WHERE " + idReference() + " = :" + idParameterName())
                .param(idParameterName(), id)
                .query(rowMapper())
                .optional();
    }

    public boolean existsById(ID id) {
        requireId(id);
        Long count = jdbcClient.sql("SELECT COUNT(*) FROM " + tableReference()
                        + " WHERE " + idReference() + " = :" + idParameterName())
                .param(idParameterName(), id)
                .query(Long.class)
                .single();
        return count != null && count > 0;
    }

    public List<T> findAll() {
        return jdbcClient.sql(baseSelectSql() + " ORDER BY " + idReference())
                .query(rowMapper())
                .list();
    }

    public PageResult<T> findPage(PageQuery query) {
        String selectSql = baseSelectSql() + " ORDER BY " + idReference();
        return pageExecutor.findPage(selectSql, "SELECT COUNT(*) FROM " + tableReference(), null, query, rowMapper());
    }

    public long count() {
        Long total = jdbcClient.sql("SELECT COUNT(*) FROM " + tableReference())
                .query(Long.class)
                .single();
        return total == null ? 0L : total;
    }

    public void insert(T entity) {
        requireEntity(entity);
        int affected = jdbcClient.sql(insertSql())
                .paramSource(insertParameters(entity))
                .update();
        if (affected != 1) {
            throw new IllegalStateException("insert affected " + affected + " rows in " + tableName());
        }
    }

    public int update(T entity) {
        requireEntity(entity);
        return jdbcClient.sql(updateSql())
                .paramSource(updateParameters(entity))
                .update();
    }

    public int deleteById(ID id) {
        requireId(id);
        return jdbcClient.sql("DELETE FROM " + tableReference() + " WHERE " + idReference() + " = :" + idParameterName())
                .param(idParameterName(), id)
                .update();
    }

    public int deleteByIds(Collection<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return jdbcClient.sql("DELETE FROM " + tableReference() + " WHERE " + idReference() + " IN (:ids)")
                .param("ids", ids)
                .update();
    }

    // INSERT ... ON CONFLICT DO NOTHING for PostgreSQL; returns true when a row was inserted.
    public boolean insertIfAbsent(T entity, String... conflictColumns) {
        requireEntity(entity);
        if (conflictColumns == null || conflictColumns.length == 0) {
            throw new IllegalArgumentException("at least one conflict column is required");
        }
        String columns = Arrays.stream(conflictColumns)
                .map(PostgresDialect::quoteIdentifier)
                .collect(Collectors.joining(", "));
        int affected = jdbcClient.sql(insertSql() + " ON CONFLICT (" + columns + ") DO NOTHING")
                .paramSource(insertParameters(entity))
                .update();
        return affected > 0;
    }

    protected void requireEntity(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
    }

    protected void requireId(ID id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
    }
}