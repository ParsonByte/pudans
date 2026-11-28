package cn.tolim.pudans.contract.search.dto;

// Update index metadata
public record SearchIndexUpdateRequest(String name, String description, Boolean enabled) {
}