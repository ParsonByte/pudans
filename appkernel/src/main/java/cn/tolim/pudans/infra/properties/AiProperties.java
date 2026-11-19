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
}
