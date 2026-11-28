package cn.tolim.pudans.apidocs;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.DefaultResourceLoader;

import javax.sql.DataSource;

// Replaces a service-declared SpringLiquibase bean with an inert one for the doc profile.
// The IAM application declares pudansLiquibase unconditionally, which would otherwise open a
// database connection during startup. Bean definition overriding is enabled in its doc profile.
@AutoConfiguration
@Profile("doc")
@ConditionalOnClass(SpringLiquibase.class)
@ConditionalOnProperty(name = "pudans.api-docs.override-liquibase-bean", havingValue = "true")
public class LiquibaseDocAutoConfiguration {

    @Bean("pudansLiquibase")
    public SpringLiquibase pudansOpenApiDocLiquibase(DataSource dataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:iam/changelog-master.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.setShouldRun(false);
        return liquibase;
    }

}