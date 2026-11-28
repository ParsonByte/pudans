package cn.tolim.pudans.iam.identitysource.feishu.domain.request;

import cn.tolim.pudans.iam.identitysource.feishu.domain.BaseRequest;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import static cn.tolim.pudans.iam.identitysource.feishu.FeiShuConstant.PAGE_SIZE;


@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class GetUserListRequest extends BaseRequest {
    // Required to get users under this department; the root department ID is 0.
    // Example value: "od-xxxxxxxxxxxxx"
    @JSONField(name = "department_id")
    private String departmentId;
    // Page size
    // Example value: 10
    // Data validation rules:
    // Maximum value: 50
    @JSONField(name = "page_size")
    private int pageSize = PAGE_SIZE;
    // Pagination token. Leave empty on the first request to start from the beginning. When more results exist, a new page_token is returned for the next request.
    // Example value: "AQD9/Rn9eij9Pm39ED40/dk53s4Ebp882DYfFaPFbz00L4CMZJrqGdzNyc8BcZtDbwVUvRmQTvyMYicnGWrde9X56TgdBuS+JKiSIkdexPw="
    @JSONField(name = "page_token")
    private String pageToken;
}
