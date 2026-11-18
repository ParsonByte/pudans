package cn.tolim.pudans.infra.feishu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lark.oapi.core.response.RawResponse;
import com.lark.oapi.service.auth.v3.model.InternalTenantAccessTokenResp;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FeishuSdkClientTest {

    @Test
    void readsTheTopLevelTenantAccessToken() {
        var response =
                response(0, "ok", "{\"code\":0,\"msg\":\"ok\",\"tenant_access_token\":\"t-123\",\"expire\":7200}");

        assertThat(FeishuSdkClient.requireTenantAccessToken(response)).isEqualTo("t-123");
    }

    @Test
    void reportsThePlatformErrorWhenTheTokenIsMissing() {
        var response = response(10003, "invalid param", "{\"code\":10003,\"data\":{},\"msg\":\"invalid param\"}");

        assertThatThrownBy(() -> FeishuSdkClient.requireTenantAccessToken(response))
                .isInstanceOf(FeishuSdkClient.FeishuSdkException.class)
                .hasMessage("飞书租户访问令牌请求失败: 10003 invalid param");
    }

    private static InternalTenantAccessTokenResp response(int code, String msg, String body) {
        var raw = new RawResponse();
        raw.setBody(body.getBytes(StandardCharsets.UTF_8));
        var response = new InternalTenantAccessTokenResp();
        response.setCode(code);
        response.setMsg(msg);
        response.setRawResponse(raw);
        return response;
    }
}
