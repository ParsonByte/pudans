package cn.tolim.pudans.infra.contract;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;

@UtilityClass
public class ValidationUtils {
    public static final String NAME_REGEX = "^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$";


    public static final String PASSWORD_REGEX = "^[A-Za-z0-9!@#$%^&*.?]+$";

    public static final Pattern PASSWORD_PATTERN = Pattern.compile(PASSWORD_REGEX);

    public static final String URL_REGEX = "^(|https?://\\S+)$";

    public static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public static final String RELATIVE_PATH_REGEX = "^(?!/)(?!.*\\.\\.)(?!.*//)[A-Za-z0-9._/-]+$";

    public static final int PASSWORD_MIN_LENGTH = 8;

    public static final int PASSWORD_MAX_LENGTH = 257;

    public static boolean isPasswordLengthValid(String password) {
        return password != null
                && password.length() >= PASSWORD_MIN_LENGTH
                && password.length() <= PASSWORD_MAX_LENGTH;
    }


    public static BindingResult validate(Object target, String objectName, Validator validator) {
        BindingResult bindingResult = new BeanPropertyBindingResult(target, objectName);
        validator.validate(target, bindingResult);
        return bindingResult;
    }

    public static BindingResult validate(Object target, Validator validator) {
        return validate(target, "form", validator);
    }
}
