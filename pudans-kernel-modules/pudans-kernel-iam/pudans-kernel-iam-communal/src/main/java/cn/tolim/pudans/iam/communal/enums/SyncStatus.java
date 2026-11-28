package cn.tolim.pudans.iam.communal.enums;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;

// Sync status
public enum SyncStatus implements BaseEnum {
    // Success
    SUCCESS("success", "成功"),
    // Failure
    FAIL("fail", "失败"),
    // Skip
    SKIP("skip", "跳过"),
    // Syncing
    PENDING("pending", "同步中");

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    SyncStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @EnumConvert
    public static SyncStatus getStatus(String code) {
        SyncStatus[] values = values();
        for (SyncStatus status : values) {
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

    @Override
    public String toString() {
        return code;
    }
}
