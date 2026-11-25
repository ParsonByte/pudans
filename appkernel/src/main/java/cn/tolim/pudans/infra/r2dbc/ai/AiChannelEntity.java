package cn.tolim.pudans.infra.r2dbc.ai;

import cn.tolim.pudans.contract.ai.AiChannel;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class AiChannelEntity {

    private UUID id;
    private String workspaceId;
    private String provider;
    private String protocol;
    private String baseUrl;
    private String secretRef;
    private Map<String, String> secretKeys = Map.of();
    private Map<String, String> credentialCiphertext = Map.of();
    private Map<String, Object> credentialStatus = Map.of();
    private String status;
    private Integer timeoutMs;
    private Integer maxConcurrency;
    private Integer weight;
    private Integer rateLimit;
    private Map<String, Object> config = Map.of();
    private Instant createdAt;
    private Instant updatedAt;

    public boolean credentialConfigured() {
        return (secretRef != null && !secretRef.isBlank())
                || (credentialCiphertext != null && !credentialCiphertext.isEmpty());
    }

    public AiChannel toContract() {
        return new AiChannel(
                id,
                workspaceId,
                provider,
                protocol,
                baseUrl,
                secretRef,
                credentialConfigured(),
                credentialStatus,
                status,
                timeoutMs,
                maxConcurrency,
                weight,
                rateLimit,
                config,
                createdAt,
                updatedAt);
    }
}
