package cn.tolim.pudans.security.authentication;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.security.resource.AuthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class ListedAuthProvider {
    @Schema(requiredMode = REQUIRED)
    String name;

    @Schema(requiredMode = REQUIRED)
    String displayName;

    String description;

    String logo;

    String website;

    String authenticationUrl;

    String helpPage;

    String bindingUrl;

    String unbindingUrl;

    AuthProvider.AuthType authType;

    Boolean isBound;

    Boolean enabled;

    int priority;

    Boolean supportsBinding;

    Boolean privileged;
}
