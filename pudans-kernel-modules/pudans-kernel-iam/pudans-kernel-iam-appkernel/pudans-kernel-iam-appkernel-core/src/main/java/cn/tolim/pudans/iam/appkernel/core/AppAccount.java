package cn.tolim.pudans.iam.appkernel.core;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
public class AppAccount implements Serializable {

    @Serial
    private static final long serialVersionUID = 3496932644393351967L;

    // Application ID
    private String appId;

    // User ID
    private String userId;

    // Account name
    private String account;

    // Account password
    private String password;

    // Default
    private Boolean defaulted;
}
