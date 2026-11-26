package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.ai.dto.AiFile;
import cn.tolim.pudans.contract.ai.dto.AiFileDeletion;
import cn.tolim.pudans.contract.ai.dto.AiFilePage;
import cn.tolim.pudans.contract.ai.dto.AiSpeechRequest;
import cn.tolim.pudans.contract.ai.dto.AiTranscriptionResult;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AiCapabilityGateway {

    String provider();

    Mono<AiFile> uploadFile(
            AiChannelEntity channel,
            Map<String, String> credentials,
            String purpose,
            String filename,
            String contentType,
            Path content,
            Long expiresAfterSeconds);

    Mono<AiFilePage> listFiles(
            AiChannelEntity channel,
            Map<String, String> credentials,
            String after,
            Integer limit,
            String order,
            String purpose);

    Mono<AiFile> retrieveFile(
            AiChannelEntity channel, Map<String, String> credentials, String fileId);

    Flux<DataBuffer> downloadFile(
            AiChannelEntity channel, Map<String, String> credentials, String fileId);

    Mono<AiFileDeletion> deleteFile(
            AiChannelEntity channel, Map<String, String> credentials, String fileId);

    Mono<AiSpeech> synthesize(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiSpeechRequest request);

    Mono<AiTranscriptionResult> transcribe(
            AiChannelEntity channel,
            Map<String, String> credentials,
            String filename,
            String contentType,
            Path content,
            AiTranscriptionOptions options);

    Mono<String> chatCompletions(
            AiChannelEntity channel, Map<String, String> credentials, Map<String, Object> body);

    Flux<ServerSentEvent<String>> chatCompletionsStream(
            AiChannelEntity channel, Map<String, String> credentials, Map<String, Object> body);

    Mono<String> messages(
            AiChannelEntity channel,
            Map<String, String> credentials,
            Map<String, Object> body,
            String beta,
            boolean versioned);

    Flux<ServerSentEvent<String>> messagesStream(
            AiChannelEntity channel,
            Map<String, String> credentials,
            Map<String, Object> body,
            String beta,
            boolean versioned);

    Mono<String> responses(
            AiChannelEntity channel, Map<String, String> credentials, Map<String, Object> body);

    Flux<ServerSentEvent<String>> responsesStream(
            AiChannelEntity channel, Map<String, String> credentials, Map<String, Object> body);
}
