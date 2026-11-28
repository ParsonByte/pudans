package cn.tolim.pudans.contract.iam.dto.user;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// User summary
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSummary implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // User ID
    private String id;

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

    // Phone area code
    private String phoneAreaCode;

    // Avatar URL
    private String avatar;

    // User status
    private String status;

    // Whether the email address is verified
    private Boolean emailVerified;

    // Whether the phone number is verified
    private Boolean phoneVerified;

    // Role codes granted to the user
    private List<String> roles;

    // User group codes the user belongs to
    private List<String> groups;

    // Last authentication time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastAuthTime;

    // Creation time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
