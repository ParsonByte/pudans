package cn.tolim.pudans.iam.infra.security.userdetails;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.Assert;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDetails extends org.springframework.security.core.userdetails.User {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String username;
    private UserType userType;
    private DataOrigin dataOrigin;
    private String email;
    private String fullName;
    private String nickName;
    private String avatar;
    private String phone;
    private String phoneAreaCode;
    private String staffNumber;
    private String externalId;
    private LocalDate expireDate;
    private LocalDateTime lastUpdatePasswordTime;
    private LocalDateTime updateTime;
    private Boolean needChangePassword;
    private Boolean phoneVerified;
    private Boolean emailVerified;
    private Collection<Group> groups = Collections.emptySet();
    private Collection<Organization> organizations = Collections.emptySet();
    private Collection<Application> applications = Collections.emptySet();

    public UserDetails(String id, String username, String password, UserType userType,
                       boolean enabled, boolean accountNonExpired, boolean credentialsNonExpired,
                       boolean accountNonLocked,
                       Collection<? extends GrantedAuthority> authorities) {
        super(username, Objects.isNull(password) ? "" : password, enabled, accountNonExpired,
                credentialsNonExpired, accountNonLocked, authorities);
        this.id = id;
        this.username = username;
        this.userType = userType;
    }

    public UserDetails(String id, String username, UserType userType, boolean enabled,
                       boolean accountNonExpired, boolean credentialsNonExpired,
                       boolean accountNonLocked,
                       Collection<? extends GrantedAuthority> authorities) {
        this(id, username, null, userType, enabled, accountNonExpired, credentialsNonExpired,
                accountNonLocked, authorities);
    }

    private static <T> Collection<T> unmodifiableCopy(Collection<T> source) {
        return Collections.unmodifiableSet(new HashSet<>(source));
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @Override
    @JsonProperty("username")
    public String getUsername() {
        return username;
    }

    @JsonProperty("userType")
    public UserType getUserType() {
        return userType;
    }

    public void setUserType(UserType userType) {
        this.userType = userType;
    }

    @JsonProperty("dataOrigin")
    public DataOrigin getDataOrigin() {
        return dataOrigin;
    }

    public void setDataOrigin(DataOrigin dataOrigin) {
        this.dataOrigin = dataOrigin;
    }

    @JsonProperty("email")
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @JsonProperty("fullName")
    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    @JsonProperty("nickName")
    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    @JsonProperty("avatar")
    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    @JsonProperty("phone")
    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    @JsonProperty("phoneAreaCode")
    public String getPhoneAreaCode() {
        return phoneAreaCode;
    }

    public void setPhoneAreaCode(String phoneAreaCode) {
        this.phoneAreaCode = phoneAreaCode;
    }

    @JsonProperty("staffNumber")
    public String getStaffNumber() {
        return staffNumber;
    }

    public void setStaffNumber(String staffNumber) {
        this.staffNumber = staffNumber;
    }

    @JsonProperty("externalId")
    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    @JsonProperty("expireDate")
    public LocalDate getExpireDate() {
        return expireDate;
    }

    public void setExpireDate(LocalDate expireDate) {
        this.expireDate = expireDate;
    }

    @JsonProperty("lastUpdatePasswordTime")
    public LocalDateTime getLastUpdatePasswordTime() {
        return lastUpdatePasswordTime;
    }

    public void setLastUpdatePasswordTime(LocalDateTime lastUpdatePasswordTime) {
        this.lastUpdatePasswordTime = lastUpdatePasswordTime;
    }

    @JsonProperty("updateTime")
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @JsonProperty("needChangePassword")
    public Boolean getNeedChangePassword() {
        return needChangePassword;
    }

    public void setNeedChangePassword(Boolean needChangePassword) {
        this.needChangePassword = needChangePassword;
    }

    @JsonProperty("phoneVerified")
    public Boolean getPhoneVerified() {
        return phoneVerified;
    }

    public void setPhoneVerified(Boolean phoneVerified) {
        this.phoneVerified = phoneVerified;
    }

    @JsonProperty("emailVerified")
    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    @JsonProperty("groups")
    public Collection<Group> getGroups() {
        return groups.isEmpty() ? Collections.emptyList() : groups;
    }

    public void setGroups(Collection<Group> groups) {
        Assert.notNull(groups, "groups 不能为空");
        this.groups = unmodifiableCopy(groups);
    }

    @JsonProperty("organizations")
    public Collection<Organization> getOrganizations() {
        return organizations.isEmpty() ? Collections.emptyList() : organizations;
    }

    public void setOrganizations(Collection<Organization> organizations) {
        Assert.notNull(organizations, "organizations 不能为空");
        this.organizations = unmodifiableCopy(organizations);
    }

    @JsonProperty("applications")
    public Collection<Application> getApplications() {
        return applications.isEmpty() ? Collections.emptyList() : applications;
    }

    public void setApplications(Collection<Application> applications) {
        Assert.notNull(applications, "applications 不能为空");
        this.applications = unmodifiableCopy(applications);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserDetails userDetails)) {
            return false;
        }
        return super.equals(other) && Objects.equals(username, userDetails.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), username);
    }

    @Override
    public String toString() {
        return username;
    }
}
