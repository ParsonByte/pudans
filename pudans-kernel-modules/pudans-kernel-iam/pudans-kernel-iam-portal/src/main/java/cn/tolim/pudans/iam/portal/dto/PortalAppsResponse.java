package cn.tolim.pudans.iam.portal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "已授权的门户应用链接")
public class PortalAppsResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 7614764845101188504L;

    // Whether the caller is authenticated
    @Schema(description = "调用方是否已认证")
    private Boolean authenticated;

    // Applications the caller may access
    @Schema(description = "已授权应用")
    private List<PortalAppView> apps = new ArrayList<>();
}