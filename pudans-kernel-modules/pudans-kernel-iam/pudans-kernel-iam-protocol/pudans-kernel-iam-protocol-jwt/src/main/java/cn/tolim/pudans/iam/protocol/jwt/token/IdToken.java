
package cn.tolim.pudans.iam.protocol.jwt.token;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;


@Data
@Builder
@Jacksonized
public class IdToken {

    private final String tokenValue;
    private final Instant issuedAt;
    private final Instant expiresAt;
}
