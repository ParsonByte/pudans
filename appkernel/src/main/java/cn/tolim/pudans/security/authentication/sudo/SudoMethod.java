package cn.tolim.pudans.security.authentication.sudo;


record SudoMethod(String name, boolean canSendCode, String maskedTarget) {}
