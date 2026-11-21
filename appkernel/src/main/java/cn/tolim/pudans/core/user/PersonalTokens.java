package cn.tolim.pudans.core.user;

import cn.tolim.pudans.security.contract.PersonalAccessToken;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface PersonalTokens {


    // 为当前登录用户创建令牌
    Mono<PersonalAccessToken> create(PersonalAccessToken patRequest);


    // 为指定用户创建令牌
    Mono<PersonalAccessToken> create(PersonalAccessToken patRequest, String username);


    // 撤销令牌，重复撤销会失败
    Mono<PersonalAccessToken> revoke(String patName, String username);


    // 恢复此前已撤销的令牌
    Mono<PersonalAccessToken> restore(String patName, String username);


    // 删除属于该用户的令牌
    Mono<PersonalAccessToken> delete(String patName, String username);


    // 读取属于该用户的令牌
    Mono<PersonalAccessToken> get(String patName, String username);


    // 生成令牌密钥并保存其哈希
    Mono<String> generateToken(PersonalAccessToken pat);


    // 列出用户的全部令牌
    Flux<PersonalAccessToken> list(String username);


    // 清理用户已过期的令牌
    Mono<Long> deleteExpired(String username);


    // 撤销用户的全部未撤销令牌，密码变更或停用时调用
    Mono<Long> revokeAll(String username);
}
