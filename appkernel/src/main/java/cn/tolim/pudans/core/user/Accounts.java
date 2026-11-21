package cn.tolim.pudans.core.user;

import cn.tolim.pudans.contract.user.User;
import java.util.Collection;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface Accounts {

    Mono<User> getUser(String username);

    Mono<User> getUserByUid(String uid);

    Mono<User> findUserByVerifiedEmail(String email);

    Mono<User> updatePassword(String username, String newPassword);

    Mono<User> updateWithRawPassword(String username, String rawPassword);


    Mono<Boolean> hasSufficientRoles(Collection<String> roles);


    Mono<User> createUser(User user);

    Mono<Boolean> confirmPassword(String username, String rawPassword);

    Flux<User> listByEmail(String email);

    Mono<Boolean> checkEmailAlreadyVerified(String email);

    String encryptPassword(String rawPassword);

    Mono<User> disable(String username);

    Mono<User> enable(String username);
}
