package cn.tolim.pudans.core.security.authentication.sudo;

import java.time.Instant;
import java.util.List;


record SudoStatus(boolean active, Instant expiresAt, List<SudoMethod> methods) {}
