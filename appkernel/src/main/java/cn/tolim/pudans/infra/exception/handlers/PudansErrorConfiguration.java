package cn.tolim.pudans.infra.exception.handlers;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.webflux.error.ErrorAttributes;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;


@Configuration
public class PudansErrorConfiguration {


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


    @Bean
    ErrorAttributes errorAttributes(MessageSource messageSource) {
        return new ProblemDetailErrorAttributes(messageSource);
    }
}
