package cn.tolim.pudans.core.user;

import cn.tolim.pudans.contract.user.User;
import java.util.Collection;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface Accounts {

    // 按资源名称读取用户，不存在时返回错误
    Mono<User> getUser(String username);

    // 按用户标识查询，空值直接返回空结果
    Mono<User> getUserByUid(String uid);

    // 仅匹配已完成验证的邮箱地址
    Mono<User> findUserByVerifiedEmail(String email);

    // 保存已加密的密码，内容相同时跳过写入
    Mono<User> updatePassword(String username, String newPassword);

    // 校验并加密原始密码后再持久化
    Mono<User> updateWithRawPassword(String username, String rawPassword);


    // 判断当前登录用户是否具备给定角色
    Mono<Boolean> hasSufficientRoles(Collection<String> roles);


    // 创建用户，名称重复时拒绝
    Mono<User> createUser(User user);

    // 校验原始密码是否与已存储密码匹配
    Mono<Boolean> confirmPassword(String username, String rawPassword);

    // 列出使用该邮箱的全部用户
    Flux<User> listByEmail(String email);

    // 判断该邮箱是否已有账号完成验证
    Mono<Boolean> checkEmailAlreadyVerified(String email);

    // 使用当前编码器加密原始密码
    String encryptPassword(String rawPassword);

    // 禁用用户并撤销其已有会话
    Mono<User> disable(String username);

    // 启用此前被禁用的用户
    Mono<User> enable(String username);
}
