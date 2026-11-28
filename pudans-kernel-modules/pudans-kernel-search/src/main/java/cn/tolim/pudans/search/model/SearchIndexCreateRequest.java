package cn.tolim.pudans.search.model;

import cn.tolim.pudans.search.constant.SearchConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SearchIndexCreateRequest(
        @NotBlank
        @Pattern(regexp = SearchConstants.CODE_PATTERN, message = "appCode has an invalid format")
        @Size(max = 64) String appCode,
        @NotBlank
        @Pattern(regexp = SearchConstants.CODE_PATTERN, message = "indexCode has an invalid format")
        @Size(max = 100) String indexCode,
        @Size(max = 200) String name,
        @Size(max = 500) String description,
        Boolean enabled) {
}