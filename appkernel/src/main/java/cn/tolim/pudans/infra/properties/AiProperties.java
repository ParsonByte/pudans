package cn.tolim.pudans.infra.properties;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@Data
@ConfigurationProperties(prefix = "pudans.ai")
public class AiProperties {

    private boolean enabled = true;

    private Worker worker = new Worker();

    private WebClient webClient = new WebClient();

    private Routing routing = new Routing();

    private Gateway gateway = new Gateway();

    private Embedding embedding = new Embedding();

    private Runtime runtime = new Runtime();

    private Observability observability = new Observability();

    @Data
    public static class Worker {

        private Duration pollInterval = Duration.ofSeconds(1);

        private int batchSize = 20;

        private int maxConcurrency = 4;

        private Duration lockTtl = Duration.ofMinutes(5);
    }

    @Data
    public static class WebClient {

        private Duration connectTimeout = Duration.ofSeconds(5);

        private Duration responseTimeout = Duration.ofSeconds(300);

        private DataSize maxInMemorySize = DataSize.ofMegabytes(16);
    }

    @Data
    public static class Routing {

        private int healthThreshold = 60;

        private Circuit circuit = new Circuit();
    }

    @Data
    public static class Circuit {

        private int failureThreshold = 5;

        private int successThreshold = 3;

        private Duration openDuration = Duration.ofSeconds(30);

        private Duration maxOpenDuration = Duration.ofMinutes(5);

        private long maxLatencyMillis = 60000;
    }

    @Data
    public static class Gateway {

        private boolean enabled = true;

        private boolean injectionEnabled = true;
    }

    @Data
    public static class Embedding {

        private int dimension = 1536;
    }

    @Data
    public static class Observability {

        private boolean otlpEnabled = false;

        private String serviceName = "pudans-appkernel";

        private String endpoint = "http://localhost:4318/v1/traces";

        private String metricsEndpoint = "http://localhost:4318/v1/metrics";

        private Duration exportInterval = Duration.ofSeconds(15);
    }

    @Data
    public static class Runtime {

        private String gatewayUrl = "http://127.0.0.1:8090/apis/api.pudans.tolim.cn/v1alpha1/aigateway";

        private int logBufferSize = 500;

        private int lockSeconds = 60;

        private int requestTimeoutSeconds = 60;

        private int logPollSeconds = 2;

        private String logFile = "/tmp/pudans-agent.log";

        private String tencentDataPlaneTemplate = "https://49983-%s.%s.tencentags.com";

        private String tencentApiVersion = "2025-09-20";
    }
}
