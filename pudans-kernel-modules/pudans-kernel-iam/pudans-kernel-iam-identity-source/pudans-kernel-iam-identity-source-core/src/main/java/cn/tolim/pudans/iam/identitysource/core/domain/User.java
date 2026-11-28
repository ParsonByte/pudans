package cn.tolim.pudans.iam.identitysource.core.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;


@Data
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 8567794739261358220L;
    // User ID
    private String userId;

    // Avatar URL
    private String avatar;

    // International dialing code, for example 86
    private String phoneAreaCode;

    // Phone number
    private String phone;

    // Email
    private String email;

    // Company email
    private String orgEmail;

    // Department ID list
    private List<String> deptIdList;

    // Status: true enabled, false disabled
    private Boolean active;

    // User details
    private UserDetail userDetail;

}
