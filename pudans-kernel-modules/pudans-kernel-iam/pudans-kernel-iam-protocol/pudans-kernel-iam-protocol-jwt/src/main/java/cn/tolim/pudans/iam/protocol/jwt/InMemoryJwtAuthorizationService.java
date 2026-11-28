
package cn.tolim.pudans.iam.protocol.jwt;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public class InMemoryJwtAuthorizationService extends AbstractJwtAuthorizationService {

    private final ConcurrentMap<String, JwtAuthentication> authorizationsById = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, JwtAuthentication> authorizationsByToken = new ConcurrentHashMap<>();

    public InMemoryJwtAuthorizationService(ApplicationServiceLoader applicationServiceLoader) {
        super(applicationServiceLoader);
    }

    @Override
    public void save(JwtAuthentication token) {
        Assert.notNull(token, "令牌不能为空");
        Assert.hasText(token.getId(), "令牌 ID 不能为空");
        Assert.hasText(token.getToken(), "令牌值不能为空");

        JwtAuthentication previousById = authorizationsById.put(token.getId(), token);
        if (previousById != null && !StringUtils.equals(previousById.getToken(), token.getToken())) {
            authorizationsByToken.remove(previousById.getToken(), previousById);
        }

        JwtAuthentication previousByToken = authorizationsByToken.put(token.getToken(), token);
        if (previousByToken != null && !StringUtils.equals(previousByToken.getId(), token.getId())) {
            authorizationsById.remove(previousByToken.getId(), previousByToken);
        }
    }

    @Override
    public void remove(JwtAuthentication authorization) {
        if (authorization == null) {
            return;
        }
        removeInternal(authorization);
    }

    @Override
    public JwtAuthentication findById(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        JwtAuthentication authorization = authorizationsById.get(id);
        if (authorization == null) {
            return null;
        }
        if (isExpired(authorization)) {
            removeInternal(authorization);
            return null;
        }
        return authorization;
    }

    @Override
    public JwtAuthentication findByToken(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        JwtAuthentication authorization = authorizationsByToken.get(token);
        if (authorization == null) {
            return null;
        }
        if (isExpired(authorization)) {
            removeInternal(authorization);
            return null;
        }
        return authorization;
    }

    private void removeInternal(JwtAuthentication authorization) {
        authorizationsById.remove(authorization.getId(), authorization);
        authorizationsByToken.remove(authorization.getToken(), authorization);
    }

    private boolean isExpired(JwtAuthentication authorization) {
        Instant expiresAt = authorization.getExpiresAt();
        return expiresAt != null && !expiresAt.isAfter(Instant.now());
    }

}
