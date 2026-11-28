
package cn.tolim.pudans.iam.protocol.jwt;

import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.protocol.core.LoginAccount;
import cn.tolim.pudans.iam.protocol.jwt.client.JwtRegisteredClient;
import org.springframework.lang.Nullable;


public interface JwtAuthorizationService {

    void save(JwtAuthentication token);

    void remove(JwtAuthentication authorization);

    @Nullable
    JwtAuthentication findById(String id);

    @Nullable
    JwtAuthentication findByToken(String token);

    LoginAccount getDefaultLoginAccount(JwtRegisteredClient client, UserDetails user);
}
