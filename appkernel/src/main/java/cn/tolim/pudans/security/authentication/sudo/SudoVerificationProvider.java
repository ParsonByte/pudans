package cn.tolim.pudans.security.authentication.sudo;

import cn.tolim.pudans.contract.user.User;
import reactor.core.publisher.Mono;


interface SudoVerificationProvider {


    // 验证方式标识，用于客户端选择与匹配
    String method();


    // 标记该方式是否需要先下发的验证码
    boolean canSendCode();


    // 判断用户当前是否具备该验证条件
    Mono<Boolean> supports(User user);


    // 向用户下发验证码，不支持时返回错误
    Mono<Void> sendCode(User user);


    // 校验用户提交的凭证，失败统一抛出异常
    Mono<Void> verify(User user, String code);


    // 返回脱敏目标供前端展示，默认不展示
    default String maskedTarget(User user) {
        return null;
    }
}
