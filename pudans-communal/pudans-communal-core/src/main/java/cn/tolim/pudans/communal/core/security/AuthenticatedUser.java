package cn.tolim.pudans.communal.core.security;

public record AuthenticatedUser(String userId, String username, String displayName, String organizationId) {
}