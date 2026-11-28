package cn.tolim.pudans.iam.communal.entity.account;

import cn.tolim.pudans.iam.communal.enums.UserStatus;
import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.Hibernate;
import org.hibernate.annotations.SoftDelete;
import org.springframework.security.core.GrantedAuthority;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Objects;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Accessors(chain = true)
@Entity
@Table(name = "pudans_user")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class UserEntity extends BaseEntity {

    // Username
    @Column(name = "username_")
    private String username;

    // Password
    @Column(name = "password_")
    private String password;

    // Email
    @Column(name = "email_")
    private String email;

    // Phone number
    @Column(name = "phone_")
    private String phone;

    // Phone number
    @Column(name = "phone_area_code")
    private String phoneAreaCode;

    // Full name
    @Column(name = "full_name")
    private String fullName;

    // Nickname
    @Column(name = "nick_name")
    private String nickName;

    // Avatar URL
    @Column(name = "avatar_")
    private String avatar;

    // Status: ENABLE, DISABLE, LOCKING
    @Column(name = "status_")
    private UserStatus status;

    // Data source
    @Column(name = "data_origin")
    private String dataOrigin;

    // Identity source ID
    @Column(name = "identity_source_id")
    private String identitySourceId;

    // Email verified
    @Column(name = "email_verified")
    private Boolean emailVerified;

    // Phone verified
    @Column(name = "phone_verified")
    private Boolean phoneVerified;

    // Authentication count
    @Column(name = "auth_total")
    private Long authTotal;

    // Last authentication IP
    @Column(name = "last_auth_ip")
    private String lastAuthIp;

    // Last authentication time
    @Column(name = "last_auth_time")
    private LocalDateTime lastAuthTime;

    // Lock time
    @Column(name = "lock_expired_time")
    private LocalDateTime lockExpiredTime;

    // Password change required
    @Column(name = "need_change_password")
    private Boolean needChangePassword;

    // Extension parameters
    @Column(name = "expand_")
    private String expand;

    // External ID
    @Column(name = "external_id")
    private String externalId;

    // Expiration time
    @Column(name = "expire_date")
    private LocalDate expireDate;

    // Last password change time
    @Column(name = "last_update_password_time")
    private LocalDateTime lastUpdatePasswordTime;

    // Stored password (plaintext)
    @Transient
    @JsonIgnore
    private String passwordPlainText;

    public Boolean isLocked() {
        return UserStatus.LOCKED.equals(this.getStatus())
                || UserStatus.PASSWORD_EXPIRED_LOCKED.equals(this.getStatus())
                || UserStatus.EXPIRED_LOCKED.equals(this.getStatus());
    }

    public Boolean isDisabled() {
        return UserStatus.DISABLED.equals(this.getStatus());
    }

    public UserDetails toUserDetails(Collection<GrantedAuthority> authorities) {
        UserDetails userDetails = new UserDetails(String.valueOf(this.getId()), this.getUsername(), this.getPassword(), UserType.USER, !isDisabled(), true, true, !isLocked(), authorities);
        userDetails.setPhone(this.getPhone());
        userDetails.setAvatar(this.getAvatar());
        userDetails.setEmail(this.getEmail());
        userDetails.setPhoneAreaCode(this.getPhoneAreaCode());
        userDetails.setPhoneVerified(this.getPhoneVerified());
        userDetails.setEmailVerified(this.getEmailVerified());
        userDetails.setLastUpdatePasswordTime(this.getLastUpdatePasswordTime());
        userDetails.setFullName(this.getFullName());
        userDetails.setNeedChangePassword(this.getNeedChangePassword());
        userDetails.setExternalId(this.getExternalId());
        userDetails.setExpireDate(this.getExpireDate());
        userDetails.setUpdateTime(this.getUpdateTime());
        userDetails.setNickName(this.getNickName());
        return userDetails;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        UserEntity user = (UserEntity) o;
        return getId() != null && Objects.equals(getId(), user.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
