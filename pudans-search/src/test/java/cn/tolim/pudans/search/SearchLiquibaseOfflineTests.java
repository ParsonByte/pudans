package cn.tolim.pudans.search;

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

// Parses the app changelog and renders PostgreSQL SQL through Liquibase offline mode
class SearchLiquibaseOfflineTests {

    @TempDir
    Path tempDir;

    @Test
    void rendersSearchDdlForPostgresqlWithoutDatabase() throws Exception {
        ResourceAccessor resourceAccessor = new SpringResourceAccessor(new DefaultResourceLoader());
        // Keep the offline history file out of the project directory
        String offlineUrl = "offline:postgresql?changeLogFile="
                + tempDir.resolve("databasechangelog.csv");
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
                new OfflineConnection(offlineUrl, resourceAccessor));
        StringWriter writer = new StringWriter();

        try (Liquibase liquibase = new Liquibase("search/changelog-master.xml",
                resourceAccessor, database)) {
            liquibase.update(new Contexts(), new LabelExpression(), writer);
        }

        String sql = writer.toString().toLowerCase(Locale.ROOT);
        assertThat(sql).contains("create table pudans_search_index");
        assertThat(sql).contains("create table pudans_search_document");
        assertThat(sql).contains("uk_search_index_code");
        assertThat(sql).contains("uk_search_document_id");
        assertThat(sql).contains("idx_search_document_scope");
        assertThat(sql).contains("jsonb");
        assertThat(sql).contains("idx_search_document_fts");
        assertThat(sql).contains("using gin");
    }
}