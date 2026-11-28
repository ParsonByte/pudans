package cn.tolim.pudans.audit.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "审计记录查询")
public class AuditQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 7165479209665861032L;

    @Schema(description = "操作者标识")
    private String actorId;

    @Schema(description = "操作者类型，例如用户或管理员")
    private String actorType;

    @Schema(description = "操作者使用的认证方式")
    private String actorAuthType;

    @Schema(description = "事件类型编码")
    private String eventType;

    @Schema(description = "事件类型编码列表，按 OR 组合")
    private List<String> eventTypes;

    @Schema(description = "审计分类编码，例如 login、session、ticket 或 authz")
    private String category;

    @Schema(description = "事件状态码：success 或 fail")
    private String eventStatus;

    @Schema(description = "HTTP 请求标识")
    private String requestId;

    @Schema(description = "登录会话标识")
    private String sessionId;

    @Schema(description = "匹配内容、参数和结果的关键字")
    private String keyword;

    @Schema(description = "事件时间范围起始值，ISO-8601")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    @Schema(description = "事件时间范围结束值，ISO-8601")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;

    @Schema(description = "页码，从 1 开始，默认 1")
    private Integer page = 1;

    @Schema(description = "每页大小，默认 20，最大 200")
    private Integer size = 20;

    @Schema(description = "排序字段：eventTime、createTime、actorId 或 eventType")
    private String sortBy = "eventTime";

    @Schema(description = "排序方向：asc 或 desc，默认 desc")
    private String sortDirection = "desc";
}