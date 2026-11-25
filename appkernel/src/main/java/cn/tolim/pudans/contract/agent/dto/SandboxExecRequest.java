package cn.tolim.pudans.contract.agent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record SandboxExecRequest(
        String command,
        String workdir,
        Integer timeoutSeconds,
        RiskLevel riskLevel) {

    public enum RiskLevel {
        LOW("low"),
        MEDIUM("medium"),
        HIGH("high");

        private final String code;

        RiskLevel(String code) {
            this.code = code;
        }

        @JsonValue
        public String getCode() {
            return code;
        }

        @JsonCreator
        public static RiskLevel fromCode(String code) {
            for (var level : values()) {
                if (level.code.equalsIgnoreCase(code)) {
                    return level;
                }
            }
            throw new IllegalArgumentException("不支持的沙箱命令风险等级：" + code);
        }
    }
}
