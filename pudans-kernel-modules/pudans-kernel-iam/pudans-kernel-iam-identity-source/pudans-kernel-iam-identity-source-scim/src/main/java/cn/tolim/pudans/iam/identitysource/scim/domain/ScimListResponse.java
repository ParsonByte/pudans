package cn.tolim.pudans.iam.identitysource.scim.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;


@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScimListResponse<T> implements Serializable {

    // Schema identifiers
    private List<String> schemas;

    // Total number of matching resources
    private Integer totalResults;

    // 1-based index of the first resource in the page
    private Integer startIndex;

    // Number of resources in the page
    private Integer itemsPerPage;

    // Page resources
    @JsonProperty("Resources")
    private List<T> resources;
}