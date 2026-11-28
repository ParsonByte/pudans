package cn.tolim.pudans.iam.identitysource.scim.domain;

import cn.tolim.pudans.iam.identitysource.scim.ScimConstants;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScimPatchOp implements Serializable {

    // Schema identifiers
    private List<String> schemas = new ArrayList<>(List.of(ScimConstants.PATCH_OP_SCHEMA));

    // Patch operations
    @JsonProperty("Operations")
    private List<ScimPatchOperation> operations;
}