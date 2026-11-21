package cn.tolim.pudans.infra.exception.handlers;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.webflux.error.ErrorAttributes;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;


@Configuration
public class PudansErrorConfiguration {


    // 注册最高优先级错误处理器，接管响应式异常响应。
    @Bean
    @Order(-1)
    ErrorWebExceptionHandler errorWebExceptionHandler(
            ErrorAttributes errorAttributes,
            WebProperties webProperties,
            ObjectProvider<ViewResolver> viewResolvers,
            ServerCodecConfigurer serverCodecConfigurer,
            ApplicationContext applicationContext) {
        var exceptionHandler = new PudansErrorWebExceptionHandler(
                errorAttributes, webProperties.getResources(), webProperties.getError(), applicationContext);
        exceptionHandler.setViewResolvers(viewResolvers.orderedStream().toList());
        exceptionHandler.setMessageWriters(serverCodecConfigurer.getWriters());
        exceptionHandler.setMessageReaders(serverCodecConfigurer.getReaders());
        return exceptionHandler;
    }


    // 提供生成问题详情的错误属性实现。
    @Bean
    ErrorAttributes errorAttributes() {
        return new ProblemDetailErrorAttributes();
    }
}
