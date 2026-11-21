package cn.tolim.pudans.core.security.authentication;

import cn.tolim.pudans.core.security.authorization.AuthorityUtils;
import lombok.Builder;
import lombok.Data;
import reactor.core.publisher.Mono;


public interface SuperAdminInitializer {

    String SUPER_ROLE_NAME = AuthorityUtils.SUPER_ROLE_NAME;


    Mono<Void> initialize(InitializationParam param);

    @Data
    @Builder
    class InitializationParam {
        private String username;
        private String password;
        private String email;
    }
}
