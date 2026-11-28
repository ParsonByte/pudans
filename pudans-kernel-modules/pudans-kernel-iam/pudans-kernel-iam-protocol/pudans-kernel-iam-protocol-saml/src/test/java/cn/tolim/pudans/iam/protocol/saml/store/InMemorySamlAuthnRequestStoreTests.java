package cn.tolim.pudans.iam.protocol.saml.store;

import cn.tolim.pudans.iam.protocol.saml.model.SamlAuthnRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;


class InMemorySamlAuthnRequestStoreTests {

    private final InMemorySamlAuthnRequestStore store = new InMemorySamlAuthnRequestStore();

    private static SamlAuthnRequest request(String id) {
        return SamlAuthnRequest.builder().id(id).issuer("https://sp.test").build();
    }

    @Test
    void savesAndFindsRequestById() {
        store.save(request("_1"), Duration.ofMinutes(5));

        assertThat(store.find("_1")).isNotNull();
        assertThat(store.find("_1").getId()).isEqualTo("_1");
    }

    @Test
    void consumesRequestOnlyOnce() {
        store.save(request("_1"), Duration.ofMinutes(5));

        assertThat(store.consume("_1")).isNotNull();
        assertThat(store.consume("_1")).isNull();
        assertThat(store.find("_1")).isNull();
    }

    @Test
    void returnsNullForExpiredRequest() {
        store.save(request("_1"), Duration.ZERO);

        assertThat(store.find("_1")).isNull();
        assertThat(store.consume("_1")).isNull();
    }

    @Test
    void removesRequest() {
        store.save(request("_1"), Duration.ofMinutes(5));

        store.remove("_1");

        assertThat(store.find("_1")).isNull();
    }
}