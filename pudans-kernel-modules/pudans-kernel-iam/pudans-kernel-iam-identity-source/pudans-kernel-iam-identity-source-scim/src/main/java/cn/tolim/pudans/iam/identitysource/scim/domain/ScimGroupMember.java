package cn.tolim.pudans.iam.identitysource.scim.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;


@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScimGroupMember implements Serializable {

    // Member identifier
    private String value;

    // Member display name
    private String display;

    // Member type, User or Group
    private String type;

    // Member reference URI
    @JsonProperty("$ref")
    private String reference;
}