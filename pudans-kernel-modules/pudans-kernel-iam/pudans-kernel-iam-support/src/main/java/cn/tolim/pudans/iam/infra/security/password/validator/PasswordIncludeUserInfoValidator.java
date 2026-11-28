package cn.tolim.pudans.iam.infra.security.password.validator;

import cn.tolim.pudans.iam.infra.security.password.PasswordValidator;
import cn.tolim.pudans.iam.infra.security.password.exception.PasswordIncludeUserInfoInvalidException;
import cn.tolim.pudans.iam.infra.security.password.exception.PasswordInvalidException;
import org.apache.commons.lang3.StringUtils;

public final class PasswordIncludeUserInfoValidator implements PasswordValidator {

    private final Boolean enabled;
    private final String fullName;
    private final String nickName;
    private final String username;
    private final String phone;
    private final String email;

    public PasswordIncludeUserInfoValidator(Boolean enabled) {
        this.enabled = enabled;
        this.fullName = "";
        this.nickName = "";
        this.username = "";
        this.phone = "";
        this.email = "";
    }

    public PasswordIncludeUserInfoValidator(String fullName, String nickName, String username,
                                            String phone, String email) {
        this.enabled = true;
        this.fullName = fullName;
        this.nickName = nickName;
        this.username = username;
        this.phone = phone;
        this.email = email;
    }

    public PasswordIncludeUserInfoValidator(Boolean enabled, String fullName, String nickName,
                                            String username, String phone, String email) {
        this.enabled = enabled;
        this.fullName = fullName;
        this.nickName = nickName;
        this.username = username;
        this.phone = phone;
        this.email = email;
    }

    @Override
    public void validate(String password) throws PasswordInvalidException {
        if (!Boolean.TRUE.equals(enabled)) {
            return;
        }
        String value = password == null ? "" : password;
        validateContains(value, username, "密码不能包含用户名");
        validateContains(value, phone, "密码不能包含手机号");
        validateContains(value, fullName, "密码不能包含姓名");
        validateContains(value, nickName, "密码不能包含昵称");
        if (StringUtils.isNotBlank(email)) {
            int atIndex = email.lastIndexOf('@');
            if (atIndex > 0) {
                validateContains(value, email.substring(0, atIndex), "密码不能包含邮箱");
            }
        }
    }

    private void validateContains(String password, String userInfo, String message) {
        if (StringUtils.isNotBlank(userInfo) && password.contains(userInfo)) {
            throw new PasswordIncludeUserInfoInvalidException(message);
        }
    }

    public String getFullName() {
        return fullName;
    }

    public String getNickName() {
        return nickName;
    }

    public String getUsername() {
        return username;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }
}
