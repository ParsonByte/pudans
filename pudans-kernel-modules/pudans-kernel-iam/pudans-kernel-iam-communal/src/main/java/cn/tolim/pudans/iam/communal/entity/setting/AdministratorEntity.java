package cn.tolim.pudans.iam.communal.entity.setting;

import cn.tolim.pudans.iam.communal.enums.UserStatus;
import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.SoftDelete;
import org.springframework.security.core.GrantedAuthority;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Collection;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Accessors(chain = true)
@Entity
@Table(name = "pudans_administrator")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class AdministratorEntity extends BaseEntity {

    public static final String USERNAME_FIELD_NAME = "username";
    public static final String PHONE_FIELD_NAME = "phone";
    public static final String EMAIL_FIELD_NAME = "email";
    @Serial
    private static final long serialVersionUID = -2619231849746900857L;
    // Username
    @Column(name = "username_")
    private String username;

    // Full name
    @Column(name = "full_name")
    private String fullName;

    // Nickname
    @Column(name = "nick_name")
    private String nickName;

    // Password
    @Column(name = "password_")
    private String password;

    // Password change required
    @Column(name = "need_change_password")
    private Boolean needChangePassword;

    // Email
    @Column(name = "email_")
    private String email;

    // Phone number
    @Column(name = "phone_")
    private String phone;

    // Phone number
    @Column(name = "phone_area_code")
    private String phoneAreaCode;

    // Phone verified
    @Column(name = "phone_verified")
    private Boolean phoneVerified;

    // Avatar URL
    @Column(name = "avatar_")
    private String avatar;

    // Status: ENABLE, DISABLE, LOCKING
    @Column(name = "status_")
    private UserStatus status;

    // Email verified
    @Column(name = "email_verified")
    private Boolean emailVerified;

    // Authentication count
    @Column(name = "auth_total")
    private Long authTotal;
    // Last authentication IP
    @Column(name = "last_auth_ip")
    private String lastAuthIp;

    // Last authentication time
    @Column(name = "last_auth_time")
    private LocalDateTime lastAuthTime;

    // Last password change time
    @Column(name = "last_update_password_time")
    private LocalDateTime lastUpdatePasswordTime;

    // Extension parameters
    @Column(name = "expand_")
    private String expand;

    public Boolean isLocked() {
        return UserStatus.LOCKED.equals(this.getStatus())
                || UserStatus.PASSWORD_EXPIRED_LOCKED.equals(this.getStatus())
                || UserStatus.EXPIRED_LOCKED.equals(this.getStatus());
    }

    public Boolean isDisabled() {
        return UserStatus.DISABLED.equals(this.getStatus());
    }

    public UserDetails toUserDetails(Collection<GrantedAuthority> authorities) {
        UserDetails userDetails = new UserDetails(this.getId(), this.getUsername(), this.getPassword(), UserType.ADMIN, !isDisabled(), true, true, !isLocked(), authorities);
        userDetails.setAvatar(this.getAvatar());
        userDetails.setPhone(this.getPhone());
        userDetails.setPhoneAreaCode(this.getPhoneAreaCode());
        userDetails.setPhoneVerified(this.getPhoneVerified());
        userDetails.setEmail(this.getEmail());
        userDetails.setEmailVerified(this.getEmailVerified());
        userDetails.setLastUpdatePasswordTime(this.getLastUpdatePasswordTime());
        userDetails.setFullName(this.getFullName());
        userDetails.setUpdateTime(this.getUpdateTime());
        userDetails.setNeedChangePassword(this.getNeedChangePassword());
        return userDetails;
    }
}
