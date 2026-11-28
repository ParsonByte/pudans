package cn.tolim.pudans.iam.authentication.core.client;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;


@Data
@Builder
public class RegisteredIdentityProviderClient<T extends IdentityProviderConfig>
        implements Serializable {

    // id
    private String id;

    // code
    private String code;

    // name
    private String name;

    // Configuration
    private T config;
}
