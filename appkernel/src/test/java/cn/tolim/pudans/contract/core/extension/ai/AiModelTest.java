package cn.tolim.pudans.contract.core.extension.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AiModelTest {

    @Test
    void derivesImageCapabilitiesAndModalities() {
        var spec = new AiModel().getSpec();
        spec.setModelType(AiModel.ModelType.IMAGE);

        assertThat(spec.effectiveCapabilities()).containsExactlyInAnyOrder(
                AiModel.Capability.IMAGE_GENERATION,
                AiModel.Capability.IMAGE_EDITING,
                AiModel.Capability.OCR);
        assertThat(spec.effectiveInputModalities())
                .containsExactly(AiModel.Modality.TEXT, AiModel.Modality.IMAGE);
        assertThat(spec.effectiveOutputModalities()).containsExactly(AiModel.Modality.IMAGE);
    }

    @Test
    void derivesVideoSpeechMusicAndThreeDCapabilities() {
        var video = new AiModel().getSpec();
        video.setModelType(AiModel.ModelType.VIDEO);
        var speech = new AiModel().getSpec();
        speech.setModelType(AiModel.ModelType.SPEECH);
        var music = new AiModel().getSpec();
        music.setModelType(AiModel.ModelType.MUSIC);
        var threeD = new AiModel().getSpec();
        threeD.setModelType(AiModel.ModelType.THREE_D);

        assertThat(video.effectiveCapabilities()).containsExactly(AiModel.Capability.VIDEO_GENERATION);
        assertThat(video.effectiveOutputModalities()).containsExactly(AiModel.Modality.VIDEO);
        assertThat(speech.effectiveCapabilities()).containsExactlyInAnyOrder(
                AiModel.Capability.SPEECH_RECOGNITION,
                AiModel.Capability.SPEECH_SYNTHESIS);
        assertThat(speech.effectiveOutputModalities())
                .containsExactly(AiModel.Modality.TEXT, AiModel.Modality.AUDIO);
        assertThat(music.effectiveCapabilities()).containsExactly(AiModel.Capability.MUSIC_GENERATION);
        assertThat(music.effectiveOutputModalities()).containsExactly(AiModel.Modality.AUDIO);
        assertThat(threeD.effectiveCapabilities())
                .containsExactly(AiModel.Capability.THREE_D_GENERATION);
        assertThat(threeD.effectiveOutputModalities()).containsExactly(AiModel.Modality.THREE_D);
    }

    @Test
    void derivesAudioUnderstandingCapability() {
        var spec = new AiModel().getSpec();
        spec.setModelType(AiModel.ModelType.AUDIO);

        assertThat(spec.effectiveCapabilities()).containsExactlyInAnyOrder(
                AiModel.Capability.AUDIO_GENERATION,
                AiModel.Capability.AUDIO_UNDERSTANDING);
    }

    @Test
    void derivesChatCapabilitiesFromLegacyFlags() {
        var spec = new AiModel().getSpec();
        spec.setModelType(AiModel.ModelType.CHAT);
        spec.setSupportsStream(true);
        spec.setSupportsTools(true);
        spec.setSupportsJson(true);
        spec.setSupportsVision(true);

        assertThat(spec.effectiveCapabilities()).containsExactlyInAnyOrder(
                AiModel.Capability.CHAT,
                AiModel.Capability.STREAM,
                AiModel.Capability.TOOLS,
                AiModel.Capability.JSON,
                AiModel.Capability.VISION);
        assertThat(spec.effectiveInputModalities())
                .containsExactly(AiModel.Modality.TEXT, AiModel.Modality.IMAGE);
        assertThat(spec.effectiveOutputModalities()).containsExactly(AiModel.Modality.TEXT);
    }

    @Test
    void explicitCapabilitiesOverrideDerivedValues() {
        var spec = new AiModel().getSpec();
        spec.setModelType(AiModel.ModelType.IMAGE);
        spec.setCapabilities(List.of(
                AiModel.Capability.IMAGE_GENERATION, AiModel.Capability.OCR));

        assertThat(spec.effectiveCapabilities()).containsExactlyInAnyOrder(
                AiModel.Capability.IMAGE_GENERATION, AiModel.Capability.OCR);
    }

    @Test
    void explicitModalitiesOverrideDerivedValues() {
        var spec = new AiModel().getSpec();
        spec.setModelType(AiModel.ModelType.EMBEDDING);
        spec.setInputModalities(List.of(
                AiModel.Modality.TEXT, AiModel.Modality.IMAGE, AiModel.Modality.VIDEO));

        assertThat(spec.effectiveInputModalities()).containsExactly(
                AiModel.Modality.TEXT, AiModel.Modality.IMAGE, AiModel.Modality.VIDEO);
        assertThat(spec.effectiveOutputModalities()).containsExactly(AiModel.Modality.TEXT);
    }
}
