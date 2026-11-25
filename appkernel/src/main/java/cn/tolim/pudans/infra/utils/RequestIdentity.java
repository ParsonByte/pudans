package cn.tolim.pudans.infra.utils;

public record RequestIdentity(String ownerId, String workspaceId) {

    public RequestIdentity {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId must not be blank");
        }
    }
}
