package cn.tolim.pudans.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "management.tracing.export.otlp.enabled=false",
                "management.logging.export.otlp.enabled=false",
                "management.otlp.metrics.export.enabled=false"
        })
class GatewayObservabilityTests {

    @LocalManagementPort
    private int managementPort;

    @LocalServerPort
    private int serverPort;

    @Test
    void hidesActuatorFromApplicationPort() {
        WebTestClient client = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + serverPort)
                .build();
        client.get()
                .uri("/apis/pudans.tolim.cn/v1alpha1/actuator/health")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void exposesJvmMetricsOnManagementPort() {
        WebTestClient client = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + managementPort)
                .build();
        client.get()
                .uri("/apis/pudans.tolim.cn/v1alpha1/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("jvm_memory_used_bytes"));
    }
}