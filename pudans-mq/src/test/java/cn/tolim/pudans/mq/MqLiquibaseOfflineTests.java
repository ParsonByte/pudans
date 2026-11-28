package cn.tolim.pudans.mq;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.OfflineConnection;
import liquibase.integration.spring.SpringResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

// Renders the app master changelog and its kernel module include through Liquibase offline mode
class MqLiquibaseOfflineTests {

    @TempDir
    Path tempDir;

    @Test
    void rendersMessageCenterDdlForPostgresqlWithoutDatabase() throws Exception {
        ResourceAccessor resourceAccessor = new SpringResourceAccessor(new DefaultResourceLoader());
        String offlineUrl = "offline:postgresql?changeLogFile="
                + tempDir.resolve("databasechangelog.csv");
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
                new OfflineConnection(offlineUrl, resourceAccessor));
        StringWriter writer = new StringWriter();

        try (Liquibase liquibase = new Liquibase("mq/changelog-master.xml",
                resourceAccessor, database)) {
            liquibase.update(new Contexts(), new LabelExpression(), writer);
        }

        String sql = writer.toString().toLowerCase(Locale.ROOT);
        assertThat(sql).contains("create table pudans_mq_message");
        assertThat(sql).contains("create table pudans_mq_delivery");
        assertThat(sql).contains("create table pudans_mq_dead_letter");
        assertThat(sql).contains("uk_pudans_mq_message_biz_key");
        assertThat(sql).contains("uk_pudans_mq_delivery_message_group");
        assertThat(sql).contains("idx_pudans_mq_message_status_next");
        assertThat(sql).contains("idx_pudans_mq_dead_letter_status");
        assertThat(sql).contains("jsonb");
    }
}