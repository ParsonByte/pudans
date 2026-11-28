package cn.tolim.pudans.iam.authorization.service;

import cn.tolim.pudans.iam.authorization.enums.PrincipalType;
import cn.tolim.pudans.iam.authorization.exception.AuthorizationErrors;
import cn.tolim.pudans.iam.authorization.repository.AuthzRoleRepository;
import cn.tolim.pudans.iam.communal.repository.account.OrganizationRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserGroupRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class PrincipalValidator {

    private final UserRepository userRepository;
    private final UserGroupRepository userGroupRepository;
    private final OrganizationRepository organizationRepository;
    private final AuthzRoleRepository authzRoleRepository;

    // Parses a principal type code
    // Parameter code: String
    // Returns: PrincipalType
    public PrincipalType parseType(String code) {
        PrincipalType type = PrincipalType.getType(code == null ? null : code.trim());
        if (type == null) {
            throw AuthorizationErrors.badRequest("Unsupported principal type '" + code + "'");
        }
        return type;
    }

    // Verifies that a principal exists, client principals are not checked
    // Parameter type: PrincipalType
    // Parameter principalId: String
    public void validate(PrincipalType type, String principalId) {
        if (principalId == null || principalId.isBlank()) {
            throw AuthorizationErrors.badRequest("principalId must not be blank");
        }
        boolean exists = switch (type) {
            case USER -> userRepository.existsById(principalId);
            case USER_GROUP -> userGroupRepository.existsById(principalId);
            case ORGANIZATION -> organizationRepository.existsById(principalId);
            case ROLE -> authzRoleRepository.existsById(principalId);
            case CLIENT -> true;
        };
        if (!exists) {
            throw AuthorizationErrors.notFound(type.getDesc() + " principal '" + principalId
                    + "' does not exist");
        }
    }
}