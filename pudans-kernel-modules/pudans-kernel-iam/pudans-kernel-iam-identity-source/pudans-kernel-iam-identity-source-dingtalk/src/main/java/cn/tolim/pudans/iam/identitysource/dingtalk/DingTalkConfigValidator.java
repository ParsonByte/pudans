package cn.tolim.pudans.iam.identitysource.dingtalk;

import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfigValidator;
import cn.tolim.pudans.iam.identitysource.core.exception.ApiCallException;
import cn.tolim.pudans.iam.identitysource.dingtalk.client.AbstractDingTalkClient;
import cn.tolim.pudans.iam.infra.validation.ValidationUtils;
import com.aliyun.dingtalkcontact_1_0.models.GetOrgAuthInfoHeaders;
import com.aliyun.dingtalkcontact_1_0.models.GetOrgAuthInfoRequest;
import com.aliyun.dingtalkoauth2_1_0.Client;
import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenRequest;
import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;


@Slf4j
public class DingTalkConfigValidator implements IdentitySourceConfigValidator<DingTalkConfig> {
    public static final String ACCESS_TOKEN_PERMISSION_DENIED = "Forbidden.AccessDenied.AccessTokenPermissionDenied";

    // Creates a DingTalk contact client.
    // Throws Exception if client initialization fails.
    public static com.aliyun.dingtalkcontact_1_0.Client getDingTalkContact() throws Exception {
        Config config = new Config();
        config.protocol = "https";
        config.regionId = "central";
        return new com.aliyun.dingtalkcontact_1_0.Client(config);
    }

    @Override
    public Boolean validate(DingTalkConfig config) {
        try {
            ValidationUtils.ValidationResult<DingTalkConfig> validationResult = ValidationUtils
                    .validateEntity(config);
            if (validationResult.isHasErrors()) {
                log.error("校验钉钉配置失败：{}", validationResult.getMessage());
                throw new ConstraintViolationException(validationResult.getConstraintViolations());
            }
            // Get AccessToken
            Client client = AbstractDingTalkClient.createClient();
            GetAccessTokenRequest request = new GetAccessTokenRequest()
                    .setAppKey(config.getAppKey()).setAppSecret(config.getAppSecret());
            GetAccessTokenResponse accessToken = client.getAccessToken(request);
            // Get enterprise information by corpId
            if (StringUtils.isNotBlank(config.getCorpId())) {
                GetOrgAuthInfoHeaders getOrgAuthInfoHeaders = new GetOrgAuthInfoHeaders();
                getOrgAuthInfoHeaders.xAcsDingtalkAccessToken = accessToken.body.getAccessToken();
                GetOrgAuthInfoRequest getOrgAuthInfoRequest = new GetOrgAuthInfoRequest()
                        .setTargetCorpId(config.getCorpId());
                getDingTalkContact().getOrgAuthInfoWithOptions(getOrgAuthInfoRequest,
                        getOrgAuthInfoHeaders, new RuntimeOptions());
            }
            return true;
        } catch (TeaException err) {
            if (!com.aliyun.teautil.Common.empty(err.code)
                    && !com.aliyun.teautil.Common.empty(err.message)) {
                // The err object contains code and message attributes to help developers identify issues
                log.error("钉钉身份源参数验证发生错误 [CODE: {}, MESSAGE: {}]", err.getCode(), err.getMessage());
                throw new ApiCallException(err.getMessage());
            }
        } catch (Exception exception) {
            TeaException err = new TeaException(exception.getMessage(), exception);
            if (!com.aliyun.teautil.Common.empty(err.code)
                    && !com.aliyun.teautil.Common.empty(err.message)) {
                // The err object contains code and message attributes to help developers identify issues
                log.error("钉钉身份源参数验证发生错误 [CODE: {}, MESSAGE: {}]", err.getCode(), err.getMessage());
                throw new ApiCallException(err.getMessage());
            }
        }
        return false;
    }
}
