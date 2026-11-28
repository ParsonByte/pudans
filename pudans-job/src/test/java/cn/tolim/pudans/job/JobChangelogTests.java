package cn.tolim.pudans.job;

import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.integration.spring.SpringResourceAccessor;
import liquibase.parser.ChangeLogParser;
import liquibase.parser.ChangeLogParserFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

// Verifies the app master changelog publishes the kernel module DDL without a database
class JobChangelogTests {

    @Test
    void appMasterChangelogIncludesTheJobExecutorChangelog() throws IOException {
        ClassPathResource master = new ClassPathResource("job/changelog-master.xml");
        assertThat(master.exists()).isTrue();

        String xml = new String(master.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(xml).contains(
                "<include file=\"1.0.0-job-changelog.xml\" relativeToChangelogFile=\"true\"/>");
        assertThat(xml).doesNotContain("classpath:");
    }

    @Test
    void liquibaseParsesTheAppChangelogWithoutADatabase() throws Exception {
        try (SpringResourceAccessor accessor =
                     new SpringResourceAccessor(new DefaultResourceLoader())) {
            ChangeLogParser parser = ChangeLogParserFactory.getInstance()
                    .getParser("xml", accessor);
            // Same classpath: form the application.yml change-log property uses
            DatabaseChangeLog changeLog = parser.parse(
                    "classpath:job/changelog-master.xml",
                    new ChangeLogParameters(), accessor);

            assertThat(changeLog.getChangeSets())
                    .extracting(ChangeSet::getId)
                    .containsExactly("1.0.0-job-executor");
        }
    }

    @Test
    void jobExecutorChangelogDefinesDefinitionAndExecutionTables() throws IOException {
        ClassPathResource ddl = new ClassPathResource("job/1.0.0-job-changelog.xml");
        assertThat(ddl.exists()).isTrue();

        String xml = new String(ddl.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(xml)
                .contains("${pudans_table_prefix}job_definition")
                .contains("${pudans_table_prefix}job_execution")
                .contains("idx_job_definition_code")
                .contains("idx_job_execution_status");
    }
}