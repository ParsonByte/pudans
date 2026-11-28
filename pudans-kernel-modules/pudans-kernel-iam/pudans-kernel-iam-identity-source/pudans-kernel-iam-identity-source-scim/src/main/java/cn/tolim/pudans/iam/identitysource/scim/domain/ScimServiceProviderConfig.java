package cn.tolim.pudans.iam.identitysource.scim.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;


@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScimServiceProviderConfig implements Serializable {

    // Schema identifiers
    private List<String> schemas;

    // Documentation URI
    private String documentationUri;

    // Patch support
    private Supported patch;

    // Bulk support
    private Bulk bulk;

    // Filter support
    private Supported filter;

    // Change password support
    private Supported changePassword;

    // Sort support
    private Supported sort;

    // ETag support
    private Supported etag;

    // Supported authentication schemes
    private List<AuthenticationScheme> authenticationSchemes;


    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Supported implements Serializable {

        // Whether the feature is supported
        private Boolean supported;
    }


    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Bulk implements Serializable {

        // Whether bulk is supported
        private Boolean supported;

        // Maximum operations per bulk request
        private Integer maxOperations;

        // Maximum payload size
        private Integer maxPayloadSize;
    }


    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AuthenticationScheme implements Serializable {

        // Scheme name
        private String name;

        // Scheme description
        private String description;

        // Specification URI
        private String specUri;

        // Documentation URI
        private String documentationUri;

        // Scheme type
        private String type;

        // Whether this is the primary scheme
        private Boolean primary;
    }
}