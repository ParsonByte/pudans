package cn.tolim.pudans.security.authentication.rememberme;

import cn.tolim.pudans.security.contract.RememberMeToken;
import reactor.core.publisher.Mono;

public interface PersistentRememberMeTokenRepository {

    // 持久化新建令牌，返回存储后的完整记录。
    Mono<RememberMeToken> createNewToken(RememberMeToken token);


    // 更新令牌值并保留原有元数据。
    Mono<RememberMeToken> updateToken(RememberMeToken token);

    // 按序列 ID 查询令牌，不存在时返回空信号。
    Mono<RememberMeToken> getTokenForSeries(String seriesId);

    // 按用户名删除全部令牌，用于密码变更等场景。
    Mono<Void> removeUserTokens(String username);

    // 按序列 ID 删除单个令牌，用于退出登录。
    Mono<Void> removeToken(String series);
}
