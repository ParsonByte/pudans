package cn.tolim.pudans.core.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.Secret;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiChannelSecretsTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    AiChannelSecretCipher cipher;

    private AiChannelSecrets secrets;

    @BeforeEach
    void setUp() {
        secrets = new AiChannelSecrets(client, cipher);
    }

    @Test
    void resolvesCredentialsFromTheReferencedSecretWithoutDecryptingStoredCiphertext() {
        var channel = channel();
        channel.setSecretRef("test-secret");
        var secret = new Secret();
        secret.setType("opaque");
        secret.setStringData(Map.of("apiKey", "resolved-value"));
        when(client.fetch(Secret.class, "test-secret")).thenReturn(Mono.just(secret));

        var result = secrets.resolve(channel).block();

        assertThat(result).containsEntry("apiKey", "resolved-value");
        verify(cipher, never()).decrypt(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsASecretReferenceCombinedWithEncryptedCredentials() {
        var channel = channel();
        channel.setSecretRef("test-secret");
        channel.setCredentialCiphertext(Map.of("apiKey", "enc:ciphertext"));

        StepVerifier.create(secrets.resolve(channel))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("ai-channel-credentials-conflict")))
                .verify();
    }

    @Test
    void decryptsStoredCredentialsWhenThereIsNoSecretReference() {
        var channel = channel();
        channel.setCredentialCiphertext(Map.of("apiKey", "enc:ciphertext"));
        when(cipher.decrypt("enc:ciphertext")).thenReturn("decrypted-value");

        var result = secrets.resolve(channel).block();

        assertThat(result).containsEntry("apiKey", "decrypted-value");
    }

    @Test
    void rejectsAChannelWithoutCredentials() {
        StepVerifier.create(secrets.resolve(channel()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("ai-channel-credentials-missing")))
                .verify();
    }

    @Test
    void exposedChannelStateContainsOnlyAConfiguredFlag() {
        var channel = channel();
        channel.setCredentialCiphertext(Map.of("apiKey", "enc:ciphertext"));

        var exposed = channel.toContract();

        assertThat(exposed.credentialConfigured()).isTrue();
        assertThat(exposed.toString()).doesNotContain("enc:ciphertext");
    }

    private static AiChannelEntity channel() {
        var channel = new AiChannelEntity();
        channel.setId(UUID.randomUUID());
        channel.setWorkspaceId("workspace-a");
        channel.setProvider("test-provider");
        channel.setProtocol("openai-compatible");
        channel.setStatus("active");
        channel.setCreatedAt(Instant.now());
        channel.setUpdatedAt(Instant.now());
        return channel;
    }
}
