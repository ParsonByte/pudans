package cn.tolim.pudans.contract.agent.dto;

public record MemoryVectorSpaceValidationResult(
        boolean valid,
        String reasonCode,
        String tableName,
        String expectedColumnType,
        String actualColumnType,
        String actualIndexMethod,
        String actualIndexOpclass) {}
