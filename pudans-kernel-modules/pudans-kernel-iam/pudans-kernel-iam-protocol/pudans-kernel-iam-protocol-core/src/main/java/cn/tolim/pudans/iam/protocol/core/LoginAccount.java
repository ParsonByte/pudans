package cn.tolim.pudans.iam.protocol.core;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Builder
public class LoginAccount implements Serializable {

    @Serial
    private static final long serialVersionUID = 3496932644393351967L;

    // Application ID
    private String appId;

    // User ID
    private String userId;

    // Account name
    private String username;

    // Account password
    private String password;

    // Default
    private Boolean isDefault;
}
