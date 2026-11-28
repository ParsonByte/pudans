package cn.tolim.pudans.iam.communal.enums.app;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.http.HttpMethod;


public enum JwtBindingType implements BaseEnum {
    // redirect
    REDIRECT("get", HttpMethod.GET, "重定向跳转"),
    // post
    POST("post", HttpMethod.POST, "页面请求跳转");

    // code
    @JsonValue
    private final String code;

    // http method
    private final HttpMethod httpMethod;
    // desc
    private final String desc;

    JwtBindingType(String code, HttpMethod httpMethod, String desc) {
        this.code = code;
        this.httpMethod = httpMethod;
        this.desc = desc;
    }

    // Returns the JwtBindingType for the given code, or null if no match.
    @EnumConvert
    public static JwtBindingType getType(String code) {
        JwtBindingType[] values = values();
        for (JwtBindingType status : values) {
            if (String.valueOf(status.getCode()).equals(code)) {
                return status;
            }
        }
        return null;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    @Override
    public String toString() {
        return this.code;
    }
}
