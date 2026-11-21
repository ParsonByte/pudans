package cn.tolim.pudans.core.infra.properties;

import lombok.Data;

@Data
public class ExtensionProperties {

    private Controller controller = new Controller();

    @Data
    public static class Controller {

        private boolean disabled;
    }
}
