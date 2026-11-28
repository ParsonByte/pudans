package cn.tolim.pudans.iam.core.configuration;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;

import javax.sql.DataSource;
import java.io.Serializable;


@Configuration
public class IamLiquibaseConfiguration implements Serializable {

    // PUDANS Liquibase
    // Parameter dataSource: DataSource
    // Returns: SpringLiquibase
    @Bean
    public SpringLiquibase pudansLiquibase(DataSource dataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setChangeLog("classpath:iam/changelog-master.xml");
        liquibase.setDataSource(dataSource);
        liquibase.setShouldRun(true);
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.setDatabaseChangeLogTable("pudans_changelog_table");
        liquibase.setDatabaseChangeLogLockTable("pudans_changelog_lock_table");
        return liquibase;
    }
}
