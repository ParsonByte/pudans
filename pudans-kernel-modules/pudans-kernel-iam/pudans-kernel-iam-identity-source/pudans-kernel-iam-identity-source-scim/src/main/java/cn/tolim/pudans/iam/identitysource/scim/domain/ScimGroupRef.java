package cn.tolim.pudans.iam.identitysource.scim.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;


@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScimGroupRef implements Serializable {

    // Group identifier
    private String value;

    // Group display name
    private String display;

    // Group type
    private String type;

    // Group reference URI
    @JsonProperty("$ref")
    private String reference;
}