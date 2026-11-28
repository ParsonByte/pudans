package cn.tolim.pudans.audit.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;

import lombok.Getter;

@Getter
public enum EventStatus {
    SUCCESS("success", "成功"),
    FAIL("fail", "失败");

    @JsonValue
    private final String code;
    private final String desc;

    EventStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @EnumConvert
    public static EventStatus getType(String code) {
        EventStatus[] values = values();
        for (EventStatus status : values) {
            if (String.valueOf(status.getCode()).equals(code)) {
                return status;
            }
        }
        return null;
    }
}
