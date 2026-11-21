package cn.tolim.pudans.security.authentication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.user.Connections;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.infra.contract.ParameterSetting;
import cn.tolim.pudans.infra.setting.ParameterSettingFetcher;
import cn.tolim.pudans.infra.setting.SecurityParameters;
import cn.tolim.pudans.security.contract.AuthProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ExtensionAuthProvidersTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ObjectProvider<ParameterSettingFetcher> environmentFetcherProvider;

    @Mock
    Connections connections;

    @Mock
    SecurityParameters securitySettings;

    @Test
    void rejectsEnablingAProviderWithoutAuthenticationConfiguration() {
        var provider = provider("custom");
        when(client.get(AuthProvider.class, "custom")).thenReturn(Mono.just(provider));
        when(securitySettings.oauth2Providers()).thenReturn(List.of());
        when(securitySettings.oidcProviders()).thenReturn(List.of());

        var service = new ExtensionAuthProviders(
                client, environmentFetcherProvider, connections, securitySettings);

        StepVerifier.create(service.enable("custom"))
                .expectError(ServerWebInputException.class)
                .verify();

        verify(client, never()).update(any());
    }

    @Test
    void rejectsDisablingAProviderControlledByAuthenticationSettings() {
        var provider = provider("corp");
        when(client.get(AuthProvider.class, "corp")).thenReturn(Mono.just(provider));
        var configured = new ParameterSetting.OAuth2Provider();
        configured.setRegistrationId("corp");
        when(securitySettings.oauth2Providers()).thenReturn(List.of(configured));

        var service = new ExtensionAuthProviders(
                client, environmentFetcherProvider, connections, securitySettings);

        StepVerifier.create(service.disable("corp"))
                .expectError(ServerWebInputException.class)
                .verify();

        verify(client, never()).update(any());
    }

    private static AuthProvider provider(String name) {
        var provider = new AuthProvider();
        var metadata = new Metadata();
        metadata.setName(name);
        provider.setMetadata(metadata);
        var spec = new AuthProvider.AuthProviderSpec();
        spec.setDisplayName(name);
        spec.setAuthenticationUrl("/oauth2/authorization/" + name);
        spec.setAuthType(AuthProvider.AuthType.OAUTH2);
        provider.setSpec(spec);
        return provider;
    }
}
