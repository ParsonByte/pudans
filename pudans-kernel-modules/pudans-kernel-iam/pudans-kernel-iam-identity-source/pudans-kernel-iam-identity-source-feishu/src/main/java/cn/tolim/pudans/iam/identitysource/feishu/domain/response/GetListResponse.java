package cn.tolim.pudans.iam.identitysource.feishu.domain.response;

import cn.tolim.pudans.iam.identitysource.feishu.domain.BaseResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;


@EqualsAndHashCode(callSuper = true)
@Data
public class GetListResponse<T extends Serializable> extends BaseResponse {
    private ListData<T> data;

    // List information
    @Data
    public static class ListData<T extends Serializable> implements Serializable {
        // Whether more items exist
        @JsonProperty("has_more")
        private boolean hasMore;
        // Pagination token returned with has_more=true when more items exist; otherwise omitted
        @JsonProperty("page_token")
        private String pageToken;
        // Department list
        private List<T> items;
    }
}
