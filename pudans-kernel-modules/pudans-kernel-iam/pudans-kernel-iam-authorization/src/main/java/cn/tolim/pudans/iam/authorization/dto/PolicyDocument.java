package cn.tolim.pudans.iam.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;


@Data
@Schema(description = "CAM 式策略文档")
public class PolicyDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 5160815466575227837L;

    // Policy document version
    @Schema(description = "策略文档版本")
    private String version;

    // Policy statements
    @Schema(description = "策略语句列表")
    private List<Statement> statement;

    @Data
    @Schema(description = "CAM 式策略语句")
    public static class Statement implements Serializable {

        @Serial
        private static final long serialVersionUID = -3316574250182028674L;

        // Statement identifier, unique within the document
        @Schema(description = "语句标识")
        private String sid;

        // Effect: Allow or Deny, Deny always wins
        @Schema(description = "效果：Allow 或 Deny")
        private String effect;

        // Actions in the form {appCode}:{action}, * matches all actions
        @Schema(description = "动作列表")
        private List<String> action;

        // Resources in the form {appCode}:{resourceType}/{resourceId}, * matches all resources
        @Schema(description = "资源列表")
        private List<String> resource;

        // Optional conditions, for example mfa, ip, orgId or time
        @Schema(description = "条件")
        private Map<String, Object> condition;
    }
}