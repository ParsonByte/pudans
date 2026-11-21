package cn.tolim.pudans.core.ai.harness.observability;

import cn.tolim.pudans.infra.properties.AiProperties;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

// 显式开启后使用原生 OpenTelemetry SDK 通过 OTLP/HTTP 导出
@Configuration
public class AiOtelConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "pudans.ai.observability", name = "otlp-enabled", havingValue = "true")
    @ConditionalOnMissingBean(OpenTelemetry.class)
    public OpenTelemetrySdk openTelemetrySdk(AiProperties properties) {
        var observability = properties.getObservability();
        var resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), observability.getServiceName())));
        var spanExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(observability.getEndpoint())
                .build();
        var tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .build();
        var metricExporter = OtlpHttpMetricExporter.builder()
                .setEndpoint(observability.getMetricsEndpoint())
                .build();
        var meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(PeriodicMetricReader.builder(metricExporter)
                        .setInterval(observability.getExportInterval())
                        .build())
                .build();
        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .build();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "pudans.ai.observability", name = "otlp-enabled", havingValue = "true")
    public HarnessObservability otelHarnessObservability(OpenTelemetry openTelemetry) {
        return new OtelHarnessObservability(openTelemetry);
    }
}