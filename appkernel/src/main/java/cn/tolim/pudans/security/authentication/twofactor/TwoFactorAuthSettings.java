package cn.tolim.pudans.security.authentication.twofactor;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;


@Data
public class TwoFactorAuthSettings {


    @Schema(requiredMode = REQUIRED)
    private boolean enabled;


    @Schema(requiredMode = REQUIRED)
    private boolean emailVerified;


    @Schema(requiredMode = REQUIRED)
    private boolean totpConfigured;


    // 开关已启用且已绑定验证器时，双重验证才真正可用。
    @Schema(requiredMode = REQUIRED)
    public boolean isAvailable() {
        return enabled && totpConfigured;
    }
}
