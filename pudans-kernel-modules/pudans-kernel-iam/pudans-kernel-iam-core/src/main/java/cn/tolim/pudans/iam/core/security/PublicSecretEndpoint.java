package cn.tolim.pudans.iam.core.security;

import cn.tolim.pudans.iam.infra.constant.IamConstants;
import cn.tolim.pudans.iam.infra.context.ServletContextService;
import cn.tolim.pudans.iam.infra.enums.SecretType;
import cn.tolim.pudans.iam.infra.result.ApiRestResult;
import cn.tolim.pudans.iam.infra.util.AesUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serial;
import java.io.Serializable;


@Tag(name = "IAM 公共密钥")
@RestController
@RequestMapping
public class PublicSecretEndpoint {

    public static final String PUBLIC_SECRET_PATH = IamConstants.V1_API_PATH + "/public_secret";

    // Get encryption key
    @Operation(summary = "获取公共密钥")
    @Validated
    @GetMapping(PUBLIC_SECRET_PATH)
    public ApiRestResult<PublicSecretResult> getPublicSecret(@RequestParam(value = "type", required = false) @NotNull(message = "秘钥类型不能为空") SecretType type) {
        // Generate a key with the utility class
        String key = AesUtils.generateKey();
        // Save session
        ServletContextService.getSession().setAttribute(type.getKey(), key);
        return ApiRestResult.ok(PublicSecretResult.builder().secret(key).build());
    }

    @Data
    @Builder
    public static class PublicSecretResult implements Serializable {

        @Serial
        private static final long serialVersionUID = 1868620270063512851L;

        // Secret
        private String secret;

    }
}
