package cn.tolim.pudans.iam.identitysource.scim.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;


@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScimMeta implements Serializable {

    // Resource type, for example User or Group
    private String resourceType;

    // Creation time
    private String created;

    // Last modification time
    private String lastModified;

    // Absolute resource location
    private String location;

    // Resource version
    private String version;
}