package cn.tolim.pudans.core.ai.harness.team;

import java.util.Objects;

public record TeamMember(String name, String agentRef, boolean lead) {

    public TeamMember {
        name = Objects.requireNonNull(name, "成员名称不能为空");
        agentRef = agentRef == null ? "" : agentRef;
    }
}