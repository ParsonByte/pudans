package cn.tolim.pudans.core.infra.properties;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Data
@ConfigurationProperties(prefix = "pudans.webauthn")
public class PasskeyProperties {


    private String rpId = "localhost";


    private String rpName = "pudans";


    private List<String> origins = new ArrayList<>(List.of("http://localhost:8090"));


    private long timeout = 60000L;
}
