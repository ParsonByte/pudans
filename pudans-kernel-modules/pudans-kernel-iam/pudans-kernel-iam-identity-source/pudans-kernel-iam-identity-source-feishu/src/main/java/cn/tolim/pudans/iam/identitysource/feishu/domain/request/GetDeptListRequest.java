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
public class GetDeptListRequest extends BaseRequest {
    // Whether to recursively get child departments
    // Example value: false
    @JSONField(name = "fetch_child")
    private boolean fetchChild = false;
    // Page size
    // Example value: 10
    // Data validation rules:
    // Maximum value: 50
    @JSONField(name = "page_size")
    private int pageSize = PAGE_SIZE;
    // Pagination token. Leave empty on the first request to start from the beginning. When more results exist, a new page_token is returned for the next request.
    // Example value: "AQD9/Rn9eij9Pm39ED40/RD/cIFmu77WxpxPB/2oHfQLZ+G8JG6tK7+ZnHiT7COhD2hMSICh/eBl7cpzU6JEC3J7COKNe4jrQ8ExwBCR"
    @JSONField(name = "page_token")
    private String pageToken;
}
