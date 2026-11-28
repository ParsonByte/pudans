package cn.tolim.pudans.iam.infra.security.userdetails;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class Application implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String code;
    private String name;
    private List<ApplicationGroup> groups;

    public Application() {
    }

    public Application(String id, String code, String name, List<ApplicationGroup> groups) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.groups = groups;
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("code")
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("groups")
    public List<ApplicationGroup> getGroups() {
        return groups;
    }

    public void setGroups(List<ApplicationGroup> groups) {
        this.groups = groups;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Application application)) {
            return false;
        }
        return Objects.equals(id, application.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, code);
    }

    public static class ApplicationGroup implements Serializable {

        private static final long serialVersionUID = 1L;

        private String id;
        private String code;
        private String name;

        public ApplicationGroup() {
        }

        public ApplicationGroup(String id, String code, String name) {
            this.id = id;
            this.code = code;
            this.name = name;
        }

        @JsonProperty("id")
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @JsonProperty("code")
        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        @JsonProperty("name")
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ApplicationGroup applicationGroup)) {
                return false;
            }
            return Objects.equals(id, applicationGroup.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, code);
        }
    }
}
