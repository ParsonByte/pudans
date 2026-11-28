
package cn.tolim.pudans.iam.protocol.jwt;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;


@Getter
public class JwtAuthentication implements Serializable {

    private String id;
    private String token;
    private String issuer;
    private String subject;
    private String audience;
    private String clientId;
    private Authentication principal;
    private Instant issuedAt;
    private Instant expiresAt;

    private JwtAuthentication(final String id, final String token, final String issuer,
                              final String subject, final String audience, final String clientId,
                              final Authentication principal, final Instant issuedAt,
                              final Instant expiresAt) {
        this.id = StringUtils.defaultIfBlank(id, UUID.randomUUID().toString());
        this.token = token;
        this.issuer = issuer;
        this.subject = subject;
        this.audience = audience;
        this.clientId = clientId;
        this.principal = principal;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public static JwtAuthenticationBuilder builder() {
        return new JwtAuthenticationBuilder();
    }

    public void setId(final String id) {
        if (id == null) {
            throw new NullPointerException("id 不能为空");
        } else {
            this.id = id;
        }
    }

    public void setToken(final String token) {
        if (token == null) {
            throw new NullPointerException("token 不能为空");
        } else {
            this.token = token;
        }
    }

    public void setIssuer(final String issuer) {
        if (issuer == null) {
            throw new NullPointerException("issuer 不能为空");
        } else {
            this.issuer = issuer;
        }
    }

    public void setSubject(final String subject) {
        if (subject == null) {
            throw new NullPointerException("subject 不能为空");
        } else {
            this.subject = subject;
        }
    }

    public void setAudience(final String audience) {
        if (audience == null) {
            throw new NullPointerException("audience 不能为空");
        } else {
            this.audience = audience;
        }
    }

    public void setClientId(final String clientId) {
        if (clientId == null) {
            throw new NullPointerException("clientId 不能为空");
        } else {
            this.clientId = clientId;
        }
    }

    public void setPrincipal(final Authentication principal) {
        if (principal == null) {
            throw new NullPointerException("principal 不能为空");
        } else {
            this.principal = principal;
        }
    }

    public void setIssuedAt(final Instant issuedAt) {
        if (issuedAt == null) {
            throw new NullPointerException("issuedAt 不能为空");
        } else {
            this.issuedAt = issuedAt;
        }
    }

    public void setExpiresAt(final Instant expiresAt) {
        if (expiresAt == null) {
            throw new NullPointerException("expiresAt 不能为空");
        } else {
            this.expiresAt = expiresAt;
        }
    }

    public static class JwtAuthenticationBuilder {

        private String id;

        private String token;

        private String issuer;

        private String subject;

        private String audience;

        private String clientId;

        private Authentication principal;

        private Instant issuedAt;

        private Instant expiresAt;

        JwtAuthenticationBuilder() {
        }

        public JwtAuthenticationBuilder id(final String id) {
            if (id == null) {
                throw new NullPointerException("id 不能为空");
            } else {
                this.id = id;
                return this;
            }
        }

        public JwtAuthenticationBuilder token(final String token) {
            if (token == null) {
                throw new NullPointerException("token 不能为空");
            } else {
                this.token = token;
                return this;
            }
        }

        public JwtAuthenticationBuilder issuer(final String issuer) {
            if (issuer == null) {
                throw new NullPointerException("issuer 不能为空");
            } else {
                this.issuer = issuer;
                return this;
            }
        }

        public JwtAuthenticationBuilder subject(final String subject) {
            if (subject == null) {
                throw new NullPointerException("subject 不能为空");
            } else {
                this.subject = subject;
                return this;
            }
        }

        public JwtAuthenticationBuilder audience(final String audience) {
            if (audience == null) {
                throw new NullPointerException("audience 不能为空");
            } else {
                this.audience = audience;
                return this;
            }
        }

        public JwtAuthenticationBuilder clientId(final String clientId) {
            if (clientId == null) {
                throw new NullPointerException("clientId 不能为空");
            } else {
                this.clientId = clientId;
                return this;
            }
        }

        public JwtAuthenticationBuilder principal(final Authentication principal) {
            if (principal == null) {
                throw new NullPointerException("principal 不能为空");
            } else {
                this.principal = principal;
                return this;
            }
        }

        public JwtAuthenticationBuilder issuedAt(final Instant issuedAt) {
            if (issuedAt == null) {
                throw new NullPointerException("issuedAt 不能为空");
            } else {
                this.issuedAt = issuedAt;
                return this;
            }
        }

        public JwtAuthenticationBuilder expiresAt(final Instant expiresAt) {
            if (expiresAt == null) {
                throw new NullPointerException("expiresAt 不能为空");
            } else {
                this.expiresAt = expiresAt;
                return this;
            }
        }

        public JwtAuthentication build() {
            return new JwtAuthentication(this.id, this.token, this.issuer, this.subject,
                    this.audience, this.clientId, this.principal, this.issuedAt, this.expiresAt);
        }
    }

}
