package cn.tolim.pudans.core.rbac.service;

import java.time.Instant;

public record TempRoleGrant(String bindingName, String roleName, Instant expiresAt) {}
