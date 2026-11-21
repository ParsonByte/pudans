package cn.tolim.pudans.core.security.authentication.sudo;


record SudoMethod(String name, boolean canSendCode, String maskedTarget) {}
