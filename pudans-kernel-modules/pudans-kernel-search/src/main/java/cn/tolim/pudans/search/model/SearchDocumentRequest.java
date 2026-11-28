package cn.tolim.pudans.search.model;

import cn.tolim.pudans.search.constant.SearchConstants;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record SearchDocumentRequest(
        @NotBlank @Size(max = 200) String documentId,
        @Size(max = 500) String title,
        @Size(max = 1000) String summary,
        @Size(max = 1_000_000) String content,
        @Size(max = SearchConstants.MAX_TAGS) List<@Size(max = 64) String> tags,
        Map<String, Object> metadata,
        @Size(max = 64) String ownerId,
        @Size(max = 64) String orgId) {

    // A document must contain at least a title or some content to be searchable
    @AssertTrue(message = "either title or content must be provided")
    public boolean isSearchable() {
        return hasText(title) || hasText(content);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}