package cn.tolim.pudans.iam.communal.entity.app.query;

import lombok.Data;


@Data
public class AppAccountQueryParam {

    // appId
    private String appId;
    // User ID
    private String userId;
    // Username
    private String username;
    // Account name
    private String account;

    // Application name
    private String appName;
}
