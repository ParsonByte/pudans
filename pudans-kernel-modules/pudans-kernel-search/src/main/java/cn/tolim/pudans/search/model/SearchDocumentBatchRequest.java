package cn.tolim.pudans.search.model;

import cn.tolim.pudans.search.constant.SearchConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SearchDocumentBatchRequest(
        @NotEmpty @Size(max = SearchConstants.MAX_BATCH_SIZE) List<@Valid SearchDocumentRequest> documents) {
}