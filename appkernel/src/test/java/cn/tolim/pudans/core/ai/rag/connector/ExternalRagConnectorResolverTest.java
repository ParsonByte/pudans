package cn.tolim.pudans.core.ai.rag.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.KnowledgeBase;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExternalRagConnectorResolverTest {

    @Test
    void selectsConnectorByProvider() {
        var dify = connector(KnowledgeBase.ExternalProvider.DIFY);
        var ragflow = connector(KnowledgeBase.ExternalProvider.RAGFLOW);
        var haystack = connector(KnowledgeBase.ExternalProvider.HAYSTACK);
        var bailian = connector(KnowledgeBase.ExternalProvider.BAILIAN);
        var resolver = new ExternalRagConnectorResolver(List.of(dify, ragflow, haystack, bailian));

        assertThat(resolver.connector(KnowledgeBase.ExternalProvider.DIFY)).isSameAs(dify);
        assertThat(resolver.connector(KnowledgeBase.ExternalProvider.RAGFLOW)).isSameAs(ragflow);
        assertThat(resolver.connector(KnowledgeBase.ExternalProvider.HAYSTACK)).isSameAs(haystack);
        assertThat(resolver.connector(KnowledgeBase.ExternalProvider.BAILIAN)).isSameAs(bailian);
    }

    @Test
    void rejectsMissingProvider() {
        var resolver = new ExternalRagConnectorResolver(
                List.of(connector(KnowledgeBase.ExternalProvider.DIFY)));

        assertThatThrownBy(() -> resolver.connector(KnowledgeBase.ExternalProvider.HAYSTACK))
                .isInstanceOf(ExternalRagException.class)
                .hasMessageContaining("HAYSTACK");
        assertThatThrownBy(() -> resolver.connector(null))
                .isInstanceOf(ExternalRagException.class);
    }

    @Test
    void rejectsDuplicateConnectorForSameProvider() {
        var first = connector(KnowledgeBase.ExternalProvider.DIFY);
        var second = connector(KnowledgeBase.ExternalProvider.DIFY);

        assertThatThrownBy(() -> new ExternalRagConnectorResolver(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DIFY");
    }

    private static ExternalRagConnector connector(KnowledgeBase.ExternalProvider provider) {
        var connector = mock(ExternalRagConnector.class);
        when(connector.provider()).thenReturn(provider);
        return connector;
    }
}