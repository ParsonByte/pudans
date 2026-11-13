package cn.tolim.pudans.infra.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import lombok.Data;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/** Development-only UI server configuration (Vite dev proxy). */
@Data
public class UiProperties {

    @Valid
    @NestedConfigurationProperty
    private final Proxy proxy = new Proxy();

    @Data
    public static class Proxy {
        /** Proxy /console, /uc, /auth HTML to the Vite dev server. */
        private boolean enabled = false;

        @NotNull
        private URI endpoint = URI.create("http://localhost:3000");
    }
}
