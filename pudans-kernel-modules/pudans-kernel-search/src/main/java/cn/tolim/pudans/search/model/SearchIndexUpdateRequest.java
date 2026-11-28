package cn.tolim.pudans.search.model;

import jakarta.validation.constraints.Size;

public record SearchIndexUpdateRequest(
        @Size(max = 200) String name,
        @Size(max = 500) String description,
        Boolean enabled) {
}