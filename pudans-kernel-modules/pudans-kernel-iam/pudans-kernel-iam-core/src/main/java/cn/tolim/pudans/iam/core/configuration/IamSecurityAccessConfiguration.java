package cn.tolim.pudans.iam.core.configuration;

import cn.tolim.pudans.iam.core.security.access.SecurityAccessExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IamSecurityAccessConfiguration {

    // Security access expression
    // Returns: SecurityAccessExpression
    @Bean(name = "sae")
    public SecurityAccessExpression securityAccessExpression() {
        return new SecurityAccessExpression();
    }
}