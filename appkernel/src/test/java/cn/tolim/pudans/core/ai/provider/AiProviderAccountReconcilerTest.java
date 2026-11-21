package cn.tolim.pudans.core.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.controller.Reconciler;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiProviderAccountReconcilerTest {

    @Mock
    ExtensionClient client;

    @Mock
    AiProviderSecretCipher secretCipher;

    private AiProviderAccountReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new AiProviderAccountReconciler(client, secretCipher);
    }

    @Test
    void encryptsTheApiKeyAndAddsTheFinalizer() {
        var account = account("a1", "sk-plain");
        when(client.fetch(AiProviderAccount.class, "a1")).thenReturn(Optional.of(account));
        when(secretCipher.isEncrypted("sk-plain")).thenReturn(false);
        when(secretCipher.encrypt("sk-plain")).thenReturn("enc:sk-plain");
        doNothing().when(client).update(any(AiProviderAccount.class));

        reconciler.reconcile(new Reconciler.Request("a1"));

        assertThat(account.getMetadata().getFinalizers()).contains("ai-provider-account-protection");
        assertThat(account.getSpec().getApiKey()).isEqualTo("enc:sk-plain");
        verify(client).update(account);
    }

    @Test
    void leavesAnAlreadyEncryptedKeyAlone() {
        var account = account("a1", "enc:sk-plain");
        account.getMetadata().setFinalizers(java.util.Set.of("ai-provider-account-protection"));
        when(client.fetch(AiProviderAccount.class, "a1")).thenReturn(Optional.of(account));
        when(secretCipher.isEncrypted("enc:sk-plain")).thenReturn(true);

        reconciler.reconcile(new Reconciler.Request("a1"));

        assertThat(account.getSpec().getApiKey()).isEqualTo("enc:sk-plain");
        verify(client, org.mockito.Mockito.never()).update(any());
    }

    private static AiProviderAccount account(String name, String apiKey) {
        var account = new AiProviderAccount();
        var metadata = new Metadata();
        metadata.setName(name);
        account.setMetadata(metadata);
        account.getSpec().setProvider("openai-primary");
        account.getSpec().setApiKey(apiKey);
        return account;
    }
}
