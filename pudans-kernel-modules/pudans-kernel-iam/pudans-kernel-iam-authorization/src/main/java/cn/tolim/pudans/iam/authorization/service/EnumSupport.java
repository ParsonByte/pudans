package cn.tolim.pudans.iam.authorization.service;

import cn.tolim.pudans.iam.authorization.enums.AuthorizationStatus;
import cn.tolim.pudans.iam.authorization.enums.RiskLevel;
import cn.tolim.pudans.iam.authorization.exception.AuthorizationErrors;


public final class EnumSupport {

    private EnumSupport() {
    }

    // Parses an authorization status, defaults to ENABLED when blank
    // Parameter code: String status code
    // Returns: AuthorizationStatus
    public static AuthorizationStatus status(String code) {
        if (code == null || code.isBlank()) {
            return AuthorizationStatus.ENABLED;
        }
        AuthorizationStatus status = AuthorizationStatus.getStatus(code.trim());
        if (status == null) {
            throw AuthorizationErrors.badRequest("Unsupported status '" + code + "'");
        }
        return status;
    }

    // Parses a risk level, defaults to LOW when blank
    // Parameter code: String risk level code
    // Returns: RiskLevel
    public static RiskLevel riskLevel(String code) {
        if (code == null || code.isBlank()) {
            return RiskLevel.LOW;
        }
        RiskLevel level = RiskLevel.getLevel(code.trim());
        if (level == null) {
            throw AuthorizationErrors.badRequest("Unsupported risk level '" + code + "'");
        }
        return level;
    }

    // Parses a principal type code
    // Parameter code: String principal type code
    // Returns: PrincipalType
    public static cn.tolim.pudans.iam.authorization.enums.PrincipalType principalType(String code) {
        cn.tolim.pudans.iam.authorization.enums.PrincipalType type =
                cn.tolim.pudans.iam.authorization.enums.PrincipalType.getType(code == null ? null
                        : code.trim());
        if (type == null) {
            throw AuthorizationErrors.badRequest("Unsupported principal type '" + code + "'");
        }
        return type;
    }
}