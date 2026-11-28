package cn.tolim.pudans.iam.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


@Data
@Schema(description = "策略文档校验结果")
public class PolicyValidationResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1661426685828096903L;

    // Whether the document is valid
    @Schema(description = "文档是否有效")
    private Boolean valid = Boolean.TRUE;

    // Validation errors
    @Schema(description = "校验错误")
    private List<String> errors = new ArrayList<>();

    // Number of statements in the document
    @Schema(description = "语句数量")
    private Integer statementCount;

    // Actions referenced by the document
    @Schema(description = "引用的动作")
    private List<String> actions = new ArrayList<>();

    // Resources referenced by the document
    @Schema(description = "引用的资源")
    private List<String> resources = new ArrayList<>();

    // Adds a validation error
    // Parameter error: String
    public void addError(String error) {
        this.valid = Boolean.FALSE;
        this.errors.add(error);
    }
}