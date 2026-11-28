package cn.tolim.pudans.iam.core.auth.user;

import cn.tolim.pudans.contract.iam.dto.ticket.UserInfo;
import cn.tolim.pudans.iam.communal.entity.account.OrganizationMemberEntity;
import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
import cn.tolim.pudans.iam.communal.entity.account.UserGroupEntity;
import cn.tolim.pudans.iam.communal.entity.account.UserGroupMemberEntity;
import cn.tolim.pudans.iam.communal.entity.setting.AdministratorEntity;
import cn.tolim.pudans.iam.communal.enums.UserStatus;
import cn.tolim.pudans.iam.communal.repository.account.OrganizationMemberRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserGroupMemberRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserGroupRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserRepository;
import cn.tolim.pudans.iam.communal.repository.setting.AdministratorRepository;
import cn.tolim.pudans.iam.communal.session.LoginSessionEntity;
import cn.tolim.pudans.iam.core.auth.IamAuthTypes;
import cn.tolim.pudans.iam.core.auth.exception.AuthenticationFailedException;
import cn.tolim.pudans.iam.core.auth.exception.SessionNotActiveException;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserAccountService {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_USER = "ROLE_USER";

    private final AdministratorRepository administratorRepository;
    private final UserRepository userRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserGroupMemberRepository userGroupMemberRepository;
    private final UserGroupRepository userGroupRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(AdministratorRepository administratorRepository,
                              UserRepository userRepository,
                              OrganizationMemberRepository organizationMemberRepository,
                              UserGroupMemberRepository userGroupMemberRepository,
                              UserGroupRepository userGroupRepository,
                              PasswordEncoder passwordEncoder) {
        this.administratorRepository = administratorRepository;
        this.userRepository = userRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.userGroupMemberRepository = userGroupMemberRepository;
        this.userGroupRepository = userGroupRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Authenticates a username and password against administrators first, then business users
    // Parameter username: String
    // Parameter rawPassword: String
    // Returns: AuthenticatedAccount
    public AuthenticatedAccount authenticate(String username, String rawPassword) {
        Optional<AdministratorEntity> administrator = administratorRepository.findByUsername(username);
        if (administrator.isPresent()) {
            AdministratorEntity entity = administrator.get();
            assertAccountUsable(entity.getStatus(), entity.getClass().getSimpleName());
            if (!matches(rawPassword, entity.getPassword())) {
                throw new AuthenticationFailedException();
            }
            return new AuthenticatedAccount(entity.getId(), entity.getUsername(),
                    IamAuthTypes.ADMIN, null, entity.getFullName());
        }
        Optional<UserEntity> user = userRepository.findByUsername(username);
        if (user.isEmpty()) {
            throw new AuthenticationFailedException();
        }
        UserEntity entity = user.get();
        assertAccountUsable(entity.getStatus(), entity.getClass().getSimpleName());
        if (entity.getExpireDate() != null && entity.getExpireDate().isBefore(LocalDate.now())) {
            throw new AuthenticationFailedException("Account is expired");
        }
        if (!matches(rawPassword, entity.getPassword())) {
            throw new AuthenticationFailedException();
        }
        return new AuthenticatedAccount(entity.getId(), entity.getUsername(), IamAuthTypes.USER,
                resolveOrganizationId(entity.getId(), IamAuthTypes.USER), entity.getFullName());
    }

    // Builds the contract user information for a global login session
    // Parameter session: LoginSessionEntity
    // Returns: UserInfo
    public UserInfo loadUserInfo(LoginSessionEntity session) {
        if (session == null) {
            throw new SessionNotActiveException();
        }
        return loadUserInfo(session.getUserId(), session.getUserType(), session.getOrgId());
    }

    // Builds the contract user information for a user id and user type
    // Parameter userId: String
    // Parameter userType: String
    // Parameter orgId: String
    // Returns: UserInfo
    public UserInfo loadUserInfo(String userId, String userType, String orgId) {
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setUserType(userType);
        userInfo.setOrgId(orgId);
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (IamAuthTypes.isAdmin(userType)) {
            AdministratorEntity entity = administratorRepository.findById(userId)
                    .orElseThrow(() -> new SessionNotActiveException("Account no longer exists"));
            if (isUnusable(entity.getStatus())) {
                throw new SessionNotActiveException("Account is disabled or locked");
            }
            userInfo.setUsername(entity.getUsername());
            userInfo.setFullName(entity.getFullName());
            userInfo.setNickName(entity.getNickName());
            userInfo.setEmail(entity.getEmail());
            userInfo.setPhone(entity.getPhone());
            userInfo.setAvatar(entity.getAvatar());
            userInfo.setEmailVerified(entity.getEmailVerified());
            userInfo.setPhoneVerified(entity.getPhoneVerified());
            userInfo.setRoles(List.of(IamAuthTypes.ADMIN));
            userInfo.setGroups(List.of());
            attributes.put("lastAuthIp", entity.getLastAuthIp());
            attributes.put("lastAuthTime", entity.getLastAuthTime());
        } else {
            UserEntity entity = userRepository.findById(userId)
                    .orElseThrow(() -> new SessionNotActiveException("Account no longer exists"));
            if (isUnusable(entity.getStatus())
                    || (entity.getExpireDate() != null && entity.getExpireDate().isBefore(LocalDate.now()))) {
                throw new SessionNotActiveException("Account is disabled, locked or expired");
            }
            userInfo.setUsername(entity.getUsername());
            userInfo.setFullName(entity.getFullName());
            userInfo.setNickName(entity.getNickName());
            userInfo.setEmail(entity.getEmail());
            userInfo.setPhone(entity.getPhone());
            userInfo.setAvatar(entity.getAvatar());
            userInfo.setEmailVerified(entity.getEmailVerified());
            userInfo.setPhoneVerified(entity.getPhoneVerified());
            userInfo.setRoles(List.of(IamAuthTypes.USER));
            userInfo.setGroups(groupCodes(userId));
            attributes.put("dataOrigin", entity.getDataOrigin());
            attributes.put("lastAuthIp", entity.getLastAuthIp());
            attributes.put("lastAuthTime", entity.getLastAuthTime());
        }
        userInfo.setAttributes(attributes);
        return userInfo;
    }

    // Loads a Spring Security principal for an active global login session
    // Parameter session: LoginSessionEntity
    // Returns: UserDetails
    public UserDetails loadUserDetails(LoginSessionEntity session) {
        UserInfo userInfo = loadUserInfo(session);
        UserType userType = IamAuthTypes.toUserType(session.getUserType());
        String role = IamAuthTypes.isAdmin(session.getUserType()) ? ROLE_ADMIN : ROLE_USER;
        UserDetails userDetails = new UserDetails(session.getUserId(), userInfo.getUsername(), userType,
                true, true, true, true, AuthorityUtils.createAuthorityList(role));
        userDetails.setFullName(userInfo.getFullName());
        userDetails.setNickName(userInfo.getNickName());
        userDetails.setEmail(userInfo.getEmail());
        userDetails.setPhone(userInfo.getPhone());
        userDetails.setAvatar(userInfo.getAvatar());
        userDetails.setEmailVerified(userInfo.getEmailVerified());
        userDetails.setPhoneVerified(userInfo.getPhoneVerified());
        return userDetails;
    }

    // Resolves the primary organization of a user
    // Parameter userId: String
    // Parameter userType: String
    // Returns: String
    public String resolveOrganizationId(String userId, String userType) {
        if (IamAuthTypes.isAdmin(userType)) {
            return null;
        }
        List<OrganizationMemberEntity> memberships = organizationMemberRepository.findAllByUserId(userId);
        return memberships.stream().map(OrganizationMemberEntity::getOrgId).findFirst().orElse(null);
    }

    // Resolves the codes of every user group the user belongs to
    // Parameter userId: String
    // Returns: List
    public List<String> groupCodes(String userId) {
        List<UserGroupMemberEntity> memberships = userGroupMemberRepository.findByUserId(userId);
        List<String> codes = new ArrayList<>();
        for (UserGroupMemberEntity membership : memberships) {
            Optional<UserGroupEntity> group = userGroupRepository.findById(membership.getGroupId());
            group.map(UserGroupEntity::getCode).ifPresent(codes::add);
        }
        return codes;
    }

    // Resolves the ids of every user group the user belongs to
    // Parameter userId: String
    // Returns: List
    public List<String> groupIds(String userId) {
        return userGroupMemberRepository.findByUserId(userId).stream()
                .map(UserGroupMemberEntity::getGroupId).toList();
    }

    // Checks whether the account still exists and is allowed to use a global login session
    // Parameter userId: String
    // Parameter userType: String
    // Returns: boolean
    public boolean isAccountUsable(String userId, String userType) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        if (IamAuthTypes.isAdmin(userType)) {
            return administratorRepository.findById(userId)
                    .map(entity -> !isUnusable(entity.getStatus())).orElse(Boolean.FALSE);
        }
        return userRepository.findById(userId).map(entity -> !isUnusable(entity.getStatus())
                && (entity.getExpireDate() == null || !entity.getExpireDate().isBefore(LocalDate.now())))
                .orElse(Boolean.FALSE);
    }

    private boolean isUnusable(UserStatus status) {
        return status != null && !UserStatus.ENABLED.equals(status);
    }

    private boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    private void assertAccountUsable(UserStatus status, String accountType) {
        if (status == null || UserStatus.ENABLED.equals(status)) {
            return;
        }
        if (UserStatus.DISABLED.equals(status)) {
            throw new AuthenticationFailedException(accountType + " account is disabled");
        }
        throw new AuthenticationFailedException(accountType + " account is locked");
    }

    // Authenticated account snapshot returned by the credential check
    public record AuthenticatedAccount(String userId, String username, String userType, String orgId,
                                       String displayName) {
    }
}