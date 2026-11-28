package cn.tolim.pudans.iam.identitysource.scim.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;


@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScimMultiValue implements Serializable {

    // Value
    private String value;

    // Display value
    private String display;

    // Value type, for example work or home
    private String type;

    // Whether this is the primary value
    private Boolean primary;

    // Reference URI
    @JsonProperty("$ref")
    private String reference;
}