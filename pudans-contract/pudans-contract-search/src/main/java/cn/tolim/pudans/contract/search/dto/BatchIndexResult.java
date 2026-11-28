package cn.tolim.pudans.contract.search.dto;

// Result of a batch index request
public record BatchIndexResult(int total, int indexed) {
}