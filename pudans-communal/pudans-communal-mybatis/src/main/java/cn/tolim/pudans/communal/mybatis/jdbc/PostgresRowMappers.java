package cn.tolim.pudans.communal.mybatis.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

// ResultSet accessors for PostgreSQL types.
public final class PostgresRowMappers {

    private PostgresRowMappers() {
    }

    public static UUID uuid(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, UUID.class);
    }

    public static UUID uuid(ResultSet resultSet, int index) throws SQLException {
        return resultSet.getObject(index, UUID.class);
    }

    public static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    public static Instant instant(ResultSet resultSet, int index) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(index);
        return timestamp == null ? null : timestamp.toInstant();
    }

    public static OffsetDateTime offsetDateTime(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class);
    }

    public static OffsetDateTime offsetDateTime(ResultSet resultSet, int index) throws SQLException {
        return resultSet.getObject(index, OffsetDateTime.class);
    }
}