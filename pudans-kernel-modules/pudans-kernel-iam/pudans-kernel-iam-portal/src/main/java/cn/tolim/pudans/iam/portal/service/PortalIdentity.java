package cn.tolim.pudans.iam.portal.service;

import cn.tolim.pudans.iam.portal.dto.PortalSessionView;

import java.util.List;

// Resolved identity of the current portal caller
public record PortalIdentity(
        String userId,
        String username,
        String displayName,
        String userType,
        String organizationId,
        String organizationName,
        String email,
        String phone,
        String avatar,
        Boolean emailVerified,
        Boolean phoneVerified,
        List<String> groups,
        PortalSessionView session) {
}