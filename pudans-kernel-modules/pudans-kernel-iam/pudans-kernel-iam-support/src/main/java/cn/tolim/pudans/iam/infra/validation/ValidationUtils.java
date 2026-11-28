package cn.tolim.pudans.iam.infra.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class ValidationUtils implements Serializable {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory()
            .getValidator();

    private ValidationUtils() {
    }

    public static <T> ValidationResult<T> validateEntity(T entity) {
        return result(VALIDATOR.validate(entity, Default.class));
    }

    public static <T> ValidationResult<T> validateEntity(T entity, Class<?>... groups) {
        return result(VALIDATOR.validate(entity, groups));
    }

    public static <T> ValidationResult<T> validateProperty(T entity, String property) {
        return result(VALIDATOR.validateProperty(entity, property, Default.class));
    }

    private static <T> ValidationResult<T> result(Set<ConstraintViolation<T>> violations) {
        ValidationResult<T> result = new ValidationResult<>();
        result.setConstraintViolations(violations);
        result.setHasErrors(!violations.isEmpty());
        Map<String, String> errors = new HashMap<>();
        violations.forEach(violation -> errors.put(violation.getPropertyPath().toString(),
                violation.getMessage()));
        result.setErrorMsg(errors);
        return result;
    }

    public static class ValidationResult<T> implements Serializable {
        private Set<ConstraintViolation<T>> constraintViolations;
        private Map<String, String> errorMsg;
        private boolean hasErrors;

        public Set<ConstraintViolation<T>> getConstraintViolations() {
            return constraintViolations;
        }

        public void setConstraintViolations(Set<ConstraintViolation<T>> constraintViolations) {
            this.constraintViolations = constraintViolations;
        }

        public Map<String, String> getErrorMsg() {
            return errorMsg;
        }

        public void setErrorMsg(Map<String, String> errorMsg) {
            this.errorMsg = errorMsg;
        }

        public boolean isHasErrors() {
            return hasErrors;
        }

        public void setHasErrors(boolean hasErrors) {
            this.hasErrors = hasErrors;
        }

        public String getMessage() {
            if (errorMsg == null || errorMsg.isEmpty()) {
                return "";
            }
            StringBuilder message = new StringBuilder();
            errorMsg.forEach((key, value) -> message.append(key).append("：").append(value)
                    .append("; "));
            return message.toString();
        }
    }
}
