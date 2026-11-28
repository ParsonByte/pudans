package cn.tolim.pudans.iam.infra.security.userdetails;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

public class Group implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    public Group() {
    }

    public Group(String id) {
        this.id = id;
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Group group)) {
            return false;
        }
        return Objects.equals(id, group.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
