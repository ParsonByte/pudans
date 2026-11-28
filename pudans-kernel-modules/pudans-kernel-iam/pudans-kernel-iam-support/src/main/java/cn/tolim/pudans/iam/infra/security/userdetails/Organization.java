package cn.tolim.pudans.iam.infra.security.userdetails;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

public class Organization implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String path;

    public Organization() {
    }

    public Organization(String id, String path) {
        this.id = id;
        this.path = path;
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("path")
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Organization organization)) {
            return false;
        }
        return Objects.equals(id, organization.id) && Objects.equals(path, organization.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, path);
    }
}
