package cn.tolim.pudans.iam.core.security.access;

import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import cn.tolim.pudans.iam.infra.security.util.SecurityUtils;

import java.util.Objects;


public class SecurityAccessExpression {

    // Check whether the principal has permission
    // Parameter authority: Permission
    // Returns: {boolean}
    public boolean hasAuthority(Object authority) {
        if (Objects.isNull(authority)) {
            return false;
        }
        UserType userType = SecurityUtils.getCurrentUserType();
        return userType.equals(authority);
    }
}
