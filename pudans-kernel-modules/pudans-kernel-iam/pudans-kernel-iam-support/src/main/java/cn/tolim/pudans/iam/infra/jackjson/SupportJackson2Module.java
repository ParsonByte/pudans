package cn.tolim.pudans.iam.infra.jackjson;

import cn.tolim.pudans.iam.infra.geo.jackson.GeoLocationJacksonModule;
import com.fasterxml.jackson.databind.Module;
import org.springframework.security.jackson2.SecurityJackson2Modules;

import java.util.ArrayList;
import java.util.List;

public final class SupportJackson2Module {
    private SupportJackson2Module() {
    }

    public static List<Module> getModules(ClassLoader classLoader) {
        List<Module> modules = new ArrayList<>(SecurityJackson2Modules.getModules(classLoader));
        modules.add(new GeoLocationJacksonModule());
        return modules;
    }
}
