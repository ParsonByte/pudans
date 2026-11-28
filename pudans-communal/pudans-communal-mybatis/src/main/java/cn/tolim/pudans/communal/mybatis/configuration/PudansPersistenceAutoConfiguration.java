package cn.tolim.pudans.communal.mybatis.configuration;

import cn.tolim.pudans.communal.mybatis.jdbc.JdbcPageExecutor;
import cn.tolim.pudans.communal.mybatis.transaction.TransactionRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration"
})
@ConditionalOnClass({JdbcClient.class, PlatformTransactionManager.class})
@ConditionalOnProperty(prefix = "pudans.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PudansPersistenceProperties.class)
public class PudansPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnSingleCandidate(PlatformTransactionManager.class)
    public TransactionRunner pudansTransactionRunner(PlatformTransactionManager transactionManager) {
        return new TransactionRunner(transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcClient.class)
    public JdbcPageExecutor pudansJdbcPageExecutor(JdbcClient jdbcClient, PudansPersistenceProperties properties) {
        return new JdbcPageExecutor(jdbcClient, properties.getQueryTimeoutSeconds());
    }
}