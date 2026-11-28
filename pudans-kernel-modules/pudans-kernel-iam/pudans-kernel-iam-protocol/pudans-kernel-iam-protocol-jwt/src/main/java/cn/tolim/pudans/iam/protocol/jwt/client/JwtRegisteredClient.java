
package cn.tolim.pudans.iam.protocol.jwt.client;

import cn.tolim.pudans.iam.protocol.core.RegisteredClient;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;


@Data
@Builder
public class JwtRegisteredClient implements RegisteredClient {

    private String id;


    private String code;


    private String clientId;


    private String clientName;


    private Instant clientIdIssuedAt;


    private String clientSecret;


    private Instant clientSecretExpiresAt;

}
