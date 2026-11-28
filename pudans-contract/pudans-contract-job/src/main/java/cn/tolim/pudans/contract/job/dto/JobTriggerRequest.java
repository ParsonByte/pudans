package cn.tolim.pudans.contract.job.dto;

// Optional overrides for a manual job trigger
public record JobTriggerRequest(String params, String triggeredBy) {
}