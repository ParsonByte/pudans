package cn.tolim.pudans.core.infra.webauthn;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.notDeleting;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;

import cn.tolim.pudans.contract.user.UserConnection;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.infra.properties.PasskeyProperties;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Slf4j
@Component
@RequiredArgsConstructor
public class Passkeys {

    public static final String PROVIDER = "passkey";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReactiveExtensionClient client;

    private final PasskeyProperties properties;

    private final ObjectConverter objectConverter = new ObjectConverter();

    private final WebAuthnManager webAuthnManager =
            WebAuthnManager.createNonStrictWebAuthnManager(objectConverter);

    public static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    public static byte[] usernameHandle(String username) {
        return username.getBytes(StandardCharsets.UTF_8);
    }

    public String newChallenge() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public Flux<UserConnection> listPasskeys(String username) {
        var options = ListOptions.builder()
                .andQuery(notDeleting())
                .andQuery(equal("spec.registrationId", PROVIDER))
                .andQuery(equal("spec.username", username))
                .build();
        return client.listAll(UserConnection.class, options, defaultSort());
    }

    public Mono<UserConnection> verifyRegistration(String username, String credentialJson, String challenge) {
        return Mono.fromCallable(() -> {
                    var registrationData = webAuthnManager.verifyRegistrationResponseJSON(
                            credentialJson, registrationParameters(challenge));
                    var attestedCredentialData = registrationData
                            .getAttestationObject()
                            .getAuthenticatorData()
                            .getAttestedCredentialData();
                    if (attestedCredentialData == null) {
                        throw new PasskeyException("注册响应不包含认证器凭据数据。");
                    }
                    var credentialId = base64Url(attestedCredentialData.getCredentialId());
                    var coseKey = objectConverter
                            .getCborConverter()
                            .writeValueAsBytes(attestedCredentialData.getCOSEKey());
                    var signCount = registrationData
                            .getAttestationObject()
                            .getAuthenticatorData()
                            .getSignCount();
                    return buildConnection(
                            username,
                            credentialId,
                            base64Url(coseKey),
                            attestedCredentialData.getAaguid(),
                            signCount,
                            registrationData.getTransports());
                })
                .flatMap(connection -> client
                        .fetch(UserConnection.class, connection.getMetadata().getName())
                        .flatMap(existing -> Mono.<UserConnection>error(
                                new PasskeyException("此通行密钥已注册。")))
                        .switchIfEmpty(Mono.defer(() -> client.create(connection))));
    }

    public Mono<AuthenticationResult> verifyAssertion(String credentialJson, String challenge) {
        return Mono.fromCallable(() -> webAuthnManager.parseAuthenticationResponseJSON(credentialJson))
                .flatMap(parsed -> {
                    var credentialId = base64Url(parsed.getCredentialId());
                    return findByCredentialId(credentialId)
                            .switchIfEmpty(Mono.error(
                                    new PasskeyException("未知的通行密钥凭据。")))
                            .flatMap(connection -> Mono.fromCallable(() -> {
                                        var authenticator = authenticator(connection);
                                        var authenticationData = webAuthnManager.verify(
                                                parsed, authenticationParameters(challenge, authenticator));
                                        return new AuthenticationResult(connection, authenticationData);
                                    })
                                    .flatMap(result -> {
                                        var newCounter = result.authenticationData()
                                                .getAuthenticatorData()
                                                .getSignCount();
                                        result.connection().getSpec().setSignCount(newCounter);
                                        result.connection().getSpec().setUpdatedAt(Instant.now());
                                        return client.update(result.connection())
                                                .thenReturn(new AuthenticationResult(result.connection(),
                                                        result.authenticationData()));
                                    }));
                });
    }

    public Mono<UserConnection> findByCredentialId(String credentialId) {
        var options = ListOptions.builder()
                .andQuery(notDeleting())
                .andQuery(equal("spec.registrationId", PROVIDER))
                .andQuery(equal("spec.providerUserId", credentialId))
                .build();
        return client.listAll(UserConnection.class, options, defaultSort()).next();
    }

    public Mono<Void> removePasskey(String username, String name) {
        return client.fetch(UserConnection.class, name)
                .filter(connection -> PROVIDER.equals(connection.getSpec().getRegistrationId()))
                .filter(connection -> username.equals(connection.getSpec().getUsername()))
                .flatMap(client::delete)
                .then();
    }

    private RegistrationParameters registrationParameters(String challenge) {
        var algorithms = List.of(
                new com.webauthn4j.data.PublicKeyCredentialParameters(
                        com.webauthn4j.data.PublicKeyCredentialType.PUBLIC_KEY,
                        com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier.ES256),
                new com.webauthn4j.data.PublicKeyCredentialParameters(
                        com.webauthn4j.data.PublicKeyCredentialType.PUBLIC_KEY,
                        com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier.RS256));
        return new RegistrationParameters(serverProperty(challenge), algorithms, false, true);
    }

    private AuthenticationParameters authenticationParameters(
            String challenge, com.webauthn4j.authenticator.Authenticator authenticator) {
        return new AuthenticationParameters(
                serverProperty(challenge), authenticator, List.of(), false, true);
    }

    private com.webauthn4j.authenticator.Authenticator authenticator(UserConnection connection) {
        try {
            var coseKey = objectConverter
                    .getCborConverter()
                    .readValue(
                            Base64.getUrlDecoder().decode(connection.getSpec().getPublicKeyCose()),
                            COSEKey.class);
            var credentialId = Base64.getUrlDecoder().decode(connection.getSpec().getProviderUserId());
            var aaguid = connection.getSpec().getAaguid() == null
                    ? AAGUID.ZERO
                    : new AAGUID(connection.getSpec().getAaguid());
            var attested = new AttestedCredentialData(aaguid, credentialId, coseKey);
            var signCount = connection.getSpec().getSignCount() == null
                    ? 0L
                    : connection.getSpec().getSignCount();
            return new com.webauthn4j.authenticator.AuthenticatorImpl(attested, null, signCount);
        } catch (Exception e) {
            throw new PasskeyException("存储的通行密钥凭据无效：" + e.getMessage());
        }
    }

    private ServerProperty serverProperty(String challenge) {
        var origins = properties.getOrigins().stream()
                .map(Origin::create)
                .collect(java.util.stream.Collectors.toSet());
        return new ServerProperty(origins, properties.getRpId(), new DefaultChallenge(
                Base64.getUrlDecoder().decode(challenge)));
    }

    private UserConnection buildConnection(
            String username,
            String credentialId,
            String coseKey,
            AAGUID aaguid,
            long signCount,
            Set<com.webauthn4j.data.AuthenticatorTransport> transports) {
        var connection = new UserConnection();
        var metadata = new Metadata();
        metadata.setName("passkey-" + credentialId.substring(0, Math.min(28, credentialId.length())));
        connection.setMetadata(metadata);
        var spec = new UserConnection.UserConnectionSpec();
        spec.setRegistrationId(PROVIDER);
        spec.setUsername(username);
        spec.setProviderUserId(credentialId);
        spec.setPublicKeyCose(coseKey);
        spec.setAaguid(aaguid == null ? AAGUID.ZERO.toString() : aaguid.toString());
        spec.setSignCount(signCount);
        spec.setUpdatedAt(Instant.now());
        if (!CollectionUtils.isEmpty(transports)) {
            spec.setTransports(transports.stream().map(Object::toString).toList());
        }
        connection.setSpec(spec);
        return connection;
    }

    public record AuthenticationResult(UserConnection connection, AuthenticationData authenticationData) {
        public String username() {
            return connection.getSpec().getUsername();
        }
    }


    public static class PasskeyException extends RuntimeException {
        public PasskeyException(String message) {
            super(message);
        }
    }
}
