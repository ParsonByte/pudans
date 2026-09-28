package cn.tolim.pudans.security.authentication.sudo;

import cn.tolim.pudans.contract.core.extension.user.User;
import reactor.core.publisher.Mono;


interface SudoVerificationProvider {


    String method();


    boolean canSendCode();


    Mono<Boolean> supports(User user);


    Mono<Void> sendCode(User user);


    Mono<Void> verify(User user, String code);


    default String maskedTarget(User user) {
        return null;
    }
}
