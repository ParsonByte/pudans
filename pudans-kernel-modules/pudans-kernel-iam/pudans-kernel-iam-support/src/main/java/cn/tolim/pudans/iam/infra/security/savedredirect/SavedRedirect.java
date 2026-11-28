package cn.tolim.pudans.iam.infra.security.savedredirect;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpMethod;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SavedRedirect implements Serializable {

    private static final long serialVersionUID = 6530884551410354148L;

    private String action;
    private String method = HttpMethod.GET.name();
    private List<Parameter> parameters = new ArrayList<>();

    @JsonProperty("action")
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    @JsonProperty("method")
    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    @JsonProperty("parameters")
    public List<Parameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<Parameter> parameters) {
        this.parameters = parameters == null ? new ArrayList<>() : parameters;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SavedRedirect redirect)) {
            return false;
        }
        return Objects.equals(action, redirect.action) && Objects.equals(method, redirect.method)
                && Objects.equals(parameters, redirect.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, method, parameters);
    }

    @Override
    public String toString() {
        return "SavedRedirect(action=" + action + ", method=" + method + ", parameters="
                + parameters + ")";
    }

    public static class Parameter implements Serializable {

        private static final long serialVersionUID = -2873886962316261616L;

        private String key;
        private String value;

        public Parameter() {
        }

        public Parameter(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @JsonProperty("key")
        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        @JsonProperty("value")
        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Parameter parameter)) {
                return false;
            }
            return Objects.equals(key, parameter.key) && Objects.equals(value, parameter.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value);
        }

        @Override
        public String toString() {
            return "Parameter(key=" + key + ", value=" + value + ")";
        }
    }
}
