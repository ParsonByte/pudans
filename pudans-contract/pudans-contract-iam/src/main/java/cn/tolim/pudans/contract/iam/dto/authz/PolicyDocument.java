package cn.tolim.pudans.contract.iam.dto.authz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

// CAM style policy document
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PolicyDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Policy document version
    private String version;

    // Policy statements
    private List<Statement> statement;

    // CAM style policy statement
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Statement implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        // Statement identifier, unique within the document
        private String sid;

        // Effect: Allow or Deny, Deny always wins
        private String effect;

        // Actions in the form {appCode}:{action}, * matches all actions
        private List<String> action;

        // Resources in the form {appCode}:{resourceType}/{resourceId}, * matches all resources
        private List<String> resource;

        // Optional conditions, for example mfa, ip, orgId or time
        private Map<String, Object> condition;
    }
}