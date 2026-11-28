package cn.tolim.pudans.contract.iam.dto.ticket;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Authenticated user information and claims
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // User ID (subject)
    private String userId;

    // Username
    private String username;

    // User type: USER or ADMIN
    private String userType;

    // Organization ID
    private String orgId;

    // Full name
    private String fullName;

    // Nickname
    private String nickName;

    // Email address
    private String email;

    // Phone number
    private String phone;

    // Avatar URL
    private String avatar;

    // Whether the email address is verified
    private Boolean emailVerified;

    // Whether the phone number is verified
    private Boolean phoneVerified;

    // Role codes granted to the user
    private List<String> roles;

    // User group codes the user belongs to
    private List<String> groups;

    // Additional claims
    private Map<String, Object> attributes;
}
