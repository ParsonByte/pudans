package cn.tolim.pudans.infra.actuator;

import java.net.URL;
import java.util.TimeZone;
import lombok.Data;


@Data
public class GlobalInfo {

    private URL externalUrl;

    private TimeZone timeZone;


    private boolean allowRegistration;

    private String favicon;


    private Boolean mustVerifyEmailOnRegistration;

    private String siteTitle;

    private String siteDescription;

    private String logoDark;

    private String logoLight;

    private String siteId;
}
