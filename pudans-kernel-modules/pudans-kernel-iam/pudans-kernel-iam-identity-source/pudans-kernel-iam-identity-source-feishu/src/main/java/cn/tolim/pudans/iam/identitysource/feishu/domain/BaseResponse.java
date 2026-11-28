package cn.tolim.pudans.iam.identitysource.feishu.domain;

import lombok.Data;

import java.io.Serializable;


@Data
public class BaseResponse implements Serializable {
    // Error code; non-zero indicates failure
    private int code;
    // Error description
    private String msg;

    public boolean isSuccess() {
        return code == 0;
    }
}
