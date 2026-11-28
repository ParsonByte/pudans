
package cn.tolim.pudans.iam.protocol.jwt.token;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.Duration;


@Data
@Builder
@RequiredArgsConstructor
public class IdTokenContext {

    private final String issuer;

    private final String subject;

    private final String audience;

    private final String sessionId;


    private final Duration idTokenTimeToLive;

    private final String privateKey;
}
