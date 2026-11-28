package cn.tolim.pudans.iam.identitysource.feishu;

import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfigValidator;
import cn.tolim.pudans.iam.identitysource.core.exception.ApiCallException;
import cn.tolim.pudans.iam.identitysource.core.exception.InvalidClientConfigException;
import cn.tolim.pudans.iam.identitysource.feishu.domain.request.GetAccessTokenRequest;
import cn.tolim.pudans.iam.identitysource.feishu.domain.response.GetAccessTokenResponse;
import cn.tolim.pudans.iam.infra.validation.ValidationUtils;
import com.alibaba.fastjson2.JSON;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import static cn.tolim.pudans.iam.identitysource.feishu.FeiShuConstant.APP_ACCESS_TOKEN_URL;


@Slf4j
public class FeiShuConfigValidator implements

        IdentitySourceConfigValidator<FeiShuConfig> {
    protected RestOperations restOperations;

    public FeiShuConfigValidator() {
        this.restOperations = new RestTemplate();
    }

    @Override
    public Boolean validate(FeiShuConfig config) throws InvalidClientConfigException {
        try {
            ValidationUtils.ValidationResult<FeiShuConfig> validationResult = ValidationUtils
                    .validateEntity(config);
            if (validationResult.isHasErrors()) {
                log.error("校验飞书配置失败：{}", validationResult.getMessage());
                throw new ConstraintViolationException(validationResult.getConstraintViolations());
            }

            cn.tolim.pudans.iam.identitysource.feishu.domain.request.GetAccessTokenRequest request = new GetAccessTokenRequest(
                    config.getAppId(), config.getAppSecret());
            GetAccessTokenResponse response = postToken(request);
            if (response.getCode() != 0) {
                throw new ApiCallException(response.getMsg());
            }
            return true;
        } catch (Exception exception) {
            log.error("飞书身份源参数验证发生错误 [MESSAGE: {}]", exception.getMessage());
            throw new ApiCallException(exception.getMessage());
        }
    }

    // Get token
    // Parameter json Object
    // Returns GetAccessTokenResponse
    private GetAccessTokenResponse postToken(Object json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> requestEntity = new HttpEntity<>(JSON.toJSONString(json), headers);
        ResponseEntity<GetAccessTokenResponse> response = restOperations.exchange(
                APP_ACCESS_TOKEN_URL, HttpMethod.POST, requestEntity, GetAccessTokenResponse.class);
        return response.getBody();
    }

}
