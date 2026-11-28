package cn.tolim.pudans.iam.identitysource.feishu.domain.response;

import cn.tolim.pudans.iam.identitysource.feishu.domain.BaseResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;


@EqualsAndHashCode(callSuper = true)
@Data
public class GetDetailsResponse<T extends Serializable> extends BaseResponse {
    private DetailsData<T> data;

    // Detailed information
    @Data
    public static class DetailsData<T extends Serializable> implements Serializable {
        private T department;
        private T user;
    }
}
