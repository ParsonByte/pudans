package cn.tolim.pudans.core.ai.provider.supplier.minimax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import cn.tolim.pudans.core.ai.model.AiModelRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class MinimaxModelAdapterTest {

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final MinimaxApiClient client = mock(MinimaxApiClient.class);

    private final MinimaxModelAdapter adapter = new MinimaxModelAdapter(client, mapper);

    @Test
    void sendsReferenceImagesWhenEditing() throws Exception {
        when(client.post(any(), any(), eq("/v1/images/edits"), any()))
                .thenReturn(Mono.just(mapper.readTree("""
                        {"data":[{"url":"https://cdn.example.com/out.png"}],
                         "usage":{"generated_images":1}}
                        """)));
        var bodyCaptor = ArgumentCaptor.forClass(JsonNode.class);

        var response = adapter.invoke(
                        provider(),
                        account(),
                        model("image-01"),
                        new AiModelRequest(
                                AiModel.Capability.IMAGE_EDITING,
                                List.of(
                                        new AiModelContent.Text("换成蓝色"),
                                        new AiModelContent.Image(
                                                "https://cdn.example.com/cat.png", null, "image/png", null, null)),
                                Map.of(),
                                false,
                                null))
                .block();

        assertThat(response.outputs()).hasSize(1);
        assertThat(((AiModelContent.Image) response.outputs().getFirst()).url())
                .isEqualTo("https://cdn.example.com/out.png");
        org.mockito.Mockito.verify(client)
                .post(any(), any(), eq("/v1/images/edits"), bodyCaptor.capture());
        var body = bodyCaptor.getValue();
        assertThat(body.path("prompt").asText()).isEqualTo("换成蓝色");
        assertThat(body.path("images").size()).isEqualTo(1);
        assertThat(body.path("images").get(0).path("image_url").asText())
                .isEqualTo("https://cdn.example.com/cat.png");
    }

    @Test
    void rejectsImageEditingWithoutReferenceImages() {
        var response = adapter.invoke(
                        provider(),
                        account(),
                        model("image-01"),
                        new AiModelRequest(
                                AiModel.Capability.IMAGE_EDITING,
                                List.of(new AiModelContent.Text("换成蓝色")),
                                Map.of(),
                                false,
                                null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> response.block())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatesAudioThroughTheTtsEndpoint() throws Exception {
        when(client.post(any(), any(), eq("/v1/t2a_v2"), any()))
                .thenReturn(Mono.just(mapper.readTree("""
                        {"data":{"audio":"00ff"},
                         "extra_info":{"audio_length":1200,"usage_characters":4,"audio_format":"mp3"}}
                        """)));
        var bodyCaptor = ArgumentCaptor.forClass(JsonNode.class);

        var response = adapter.invoke(
                        provider(),
                        account(),
                        model("speech-02-hd"),
                        new AiModelRequest(
                                AiModel.Capability.AUDIO_GENERATION,
                                List.of(new AiModelContent.Text("你好")),
                                Map.of(),
                                false,
                                null))
                .block();

        assertThat(response.outputs()).hasSize(1);
        var audio = (AiModelContent.Audio) response.outputs().getFirst();
        assertThat(audio.data()).isEqualTo("AP8=");
        assertThat(audio.format()).isEqualTo("mp3");
        assertThat(response.usage().audioSeconds()).isEqualTo(1);
        assertThat(response.usage().speechCharacters()).isEqualTo(4);
        org.mockito.Mockito.verify(client)
                .post(any(), any(), eq("/v1/t2a_v2"), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue().path("text").asText()).isEqualTo("你好");
    }

    @Test
    void passesTheAsrLanguageHeader() {
        when(client.upload(any(), any(), eq("/v1/speech_to_text"), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(mapper.createObjectNode().put("text", "hello")));
        var headersCaptor = ArgumentCaptor.forClass(Map.class);

        adapter.invoke(
                        provider(),
                        account(),
                        model("speech-01"),
                        new AiModelRequest(
                                AiModel.Capability.SPEECH_RECOGNITION,
                                List.of(new AiModelContent.Audio(
                                        null,
                                        java.util.Base64.getEncoder().encodeToString(new byte[] {1, 2}),
                                        "audio/wav",
                                        null,
                                        "wav",
                                        null)),
                                Map.of("language", mapper.getNodeFactory().textNode("en")),
                                false,
                                null))
                .block();

        org.mockito.Mockito.verify(client)
                .upload(any(), any(), eq("/v1/speech_to_text"), any(), headersCaptor.capture(),
                        any(), any(), any());
        assertThat(headersCaptor.getValue()).containsEntry("language", "en");
    }

    private static AiProvider provider() {
        var provider = new AiProvider();
        provider.getSpec().setProtocol(AiProvider.Protocol.MINIMAX);
        provider.getSpec().setBaseUrl("https://api.minimax.example.com");
        return provider;
    }

    private static AiProviderAccount account() {
        return new AiProviderAccount();
    }

    private static AiModel model(String modelCode) {
        var model = new AiModel();
        model.getSpec().setModelCode(modelCode);
        return model;
    }
}
