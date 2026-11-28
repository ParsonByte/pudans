package cn.tolim.pudans.iam.identitysource.scim.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;


@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScimResource implements Serializable {

    // Schema identifiers
    private List<String> schemas;

    // Server assigned resource identifier
    private String id;

    // External identifier from the provisioning client
    private String externalId;

    // Resource metadata
    private ScimMeta meta;
}