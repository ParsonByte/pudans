package cn.tolim.pudans.security.authentication.sudo;

import java.time.Instant;
import java.util.List;


record SudoStatus(boolean active, Instant expiresAt, List<SudoMethod> methods) {}
