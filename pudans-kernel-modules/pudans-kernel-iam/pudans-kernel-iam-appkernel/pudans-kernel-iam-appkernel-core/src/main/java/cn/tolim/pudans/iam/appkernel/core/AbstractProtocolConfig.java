package cn.tolim.pudans.iam.appkernel.core;

import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;


@Data
@SuperBuilder
public class AbstractProtocolConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 7006674647924828758L;

    // Application ID
    private String appId;

    // Application name
    private String appName;

    // Client ID
    private String clientId;

    // Client secret
    private String clientSecret;

    // Application code
    private String appCode;

    // Application template
    private String appTemplate;
}
