package cn.tolim.pudans.iam.identitysource.scim.domain;

import cn.tolim.pudans.iam.identitysource.scim.ScimConstants;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScimError implements Serializable {

    // Schema identifiers
    private List<String> schemas = new ArrayList<>(List.of(ScimConstants.ERROR_SCHEMA));

    // HTTP status code
    private Integer status;

    // SCIM error type
    private String scimType;

    // Human readable detail
    private String detail;
}