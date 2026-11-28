package cn.tolim.pudans.iam.identitysource.scim.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;


@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScimName implements Serializable {

    // Full formatted name
    private String formatted;

    // Family name
    private String familyName;

    // Given name
    private String givenName;

    // Middle name
    private String middleName;

    // Honorific prefix
    private String honorificPrefix;

    // Honorific suffix
    private String honorificSuffix;
}