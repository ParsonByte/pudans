package cn.tolim.pudans.core.agent.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.agent.Sandbox;
import cn.tolim.pudans.contract.agent.SandboxOperation;
import cn.tolim.pudans.contract.agent.SandboxSnapshot;
import cn.tolim.pudans.contract.agent.dto.SandboxEvent;
import cn.tolim.pudans.contract.agent.dto.SandboxExecResult;
import cn.tolim.pudans.contract.agent.dto.SandboxFileResult;
import cn.tolim.pudans.contract.agent.dto.SandboxOperationPage;
import cn.tolim.pudans.contract.agent.dto.SandboxPage;
import cn.tolim.pudans.core.agent.sandbox.SandboxAudit;
import cn.tolim.pudans.core.agent.sandbox.SandboxEvents;
import cn.tolim.pudans.core.agent.sandbox.SandboxProperties;
import cn.tolim.pudans.core.agent.sandbox.Sandboxes;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.utils.Digests;
import cn.tolim.pudans.infra.r2dbc.creation.CreationCursor;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class SandboxEndpointTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final RequestIdentity ALICE = new RequestIdentity("alice", "workspace-a");

    private static final UUID SANDBOX_ID = UUID.randomUUID();

    @Mock
    Sandboxes sandboxes;

    @Mock
    SandboxEvents events;

    @Mock
    ReactiveRequestIdentity identity;

    @Mock
    SandboxAudit audits;

    private WebTestClient client;

    private SandboxProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SandboxProperties();
        var endpoint = new SandboxEndpoint(sandboxes, events, identity, audits, properties);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
        lenient().when(audits.record(any(), any(), any(), anyBoolean(), anyMap()))
                .thenReturn(Mono.empty());
        lenient().when(audits.failure(any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void createsASandboxWithTheIdempotencyHeader() {
        var sandbox = sandbox(Sandbox.Status.READY);
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(sandboxes.create(eq(ALICE), any(), eq("request-1")))
                .thenReturn(Mono.just(new Sandboxes.Creation(sandbox, true)));

        client.post()
                .uri("/sandboxes")
                .header("Idempotency-Key", "request-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRequest())
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(SANDBOX_ID.toString())
                .jsonPath("$.status")
                .isEqualTo("ready");

        verify(audits).record(
                any(),
                eq("CREATE_SANDBOX"),
                eq(sandbox),
                eq(true),
                anyMap());
    }

    @Test
    void listsEverySandboxForAdministrativeInspection() {
        var sandbox = sandbox(Sandbox.Status.READY);
        when(sandboxes.listAll(isNull(), eq(20)))
                .thenReturn(Mono.just(new SandboxPage(List.of(sandbox), null, 20)));

        client.get()
                .uri("/sandboxes/-/all?limit=20")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items[0].ownerId")
                .isEqualTo("alice");

        verify(sandboxes).listAll(isNull(), eq(20));
    }

    @Test
    void returnsOkWhenTheCreationReplays() {
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(sandboxes.create(eq(ALICE), any(), eq("request-1")))
                .thenReturn(Mono.just(new Sandboxes.Creation(
                        sandbox(Sandbox.Status.READY), false)));

        client.post()
                .uri("/sandboxes")
                .header("Idempotency-Key", "request-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRequest())
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void rejectsAnEmptyCreateBodyBeforeResolvingIdentity() {
        client.post()
                .uri("/sandboxes")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(identity, sandboxes, events, audits);
    }

    @Test
    void listsSandboxesWithTheDecodedCursorAndLimit() {
        var position = new CreationCursor.Position(NOW, UUID.randomUUID());
        var cursor = CreationCursor.encode(position.sortTime(), position.id());
        var page = new SandboxPage(List.of(sandbox(Sandbox.Status.READY)), "next", 5);
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(sandboxes.list(ALICE, position, 5)).thenReturn(Mono.just(page));

        client.get()
                .uri(uri -> uri.path("/sandboxes")
                        .queryParam("cursor", cursor)
                        .queryParam("limit", 5)
                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items[0].id")
                .isEqualTo(SANDBOX_ID.toString())
                .jsonPath("$.nextCursor")
                .isEqualTo("next");
    }

    @Test
    void rejectsMalformedListParametersBeforeResolvingIdentity() {
        client.get()
                .uri("/sandboxes?cursor=not-a-cursor")
                .exchange()
                .expectStatus()
                .isBadRequest();
        client.get()
                .uri("/sandboxes?limit=many")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(identity, sandboxes, events, audits);
    }

    @Test
    void viewsOneOwnedSandbox() {
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(sandboxes.view(ALICE, SANDBOX_ID))
                .thenReturn(Mono.just(sandbox(Sandbox.Status.READY)));

        client.get()
                .uri("/sandboxes/{id}", SANDBOX_ID)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(SANDBOX_ID.toString());
    }

    @Test
    void executesACommandAndAuditsTheOperationSummary() {
        var result = new SandboxExecResult(
                UUID.randomUUID(),
                SandboxOperation.Status.SUCCEEDED,
                0,
                "ok",
                "",
                false,
                NOW,
                NOW.plusSeconds(1));
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(sandboxes.view(ALICE, SANDBOX_ID))
                .thenReturn(Mono.just(sandbox(Sandbox.Status.READY)));
        when(sandboxes.exec(eq(ALICE), eq(SANDBOX_ID), any())).thenReturn(Mono.just(result));

        client.post()
                .uri("/sandboxes/{id}/-/exec", SANDBOX_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"command":"pwd","workdir":".","timeoutSeconds":30,"riskLevel":"low"}
                        """)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.operationId")
                .isEqualTo(result.operationId().toString())
                .jsonPath("$.exitCode")
                .isEqualTo(0);

        verify(audits).record(
                any(),
                eq("EXEC_SANDBOX"),
                any(Sandbox.class),
                eq(true),
                anyMap());
    }

    @Test
    void auditsAFailedExecutionAndPreservesTheApiError() {
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(sandboxes.view(ALICE, SANDBOX_ID))
                .thenReturn(Mono.just(sandbox(Sandbox.Status.READY)));
        when(sandboxes.exec(eq(ALICE), eq(SANDBOX_ID), any())).thenReturn(Mono.error(
                new ApiException(
                        HttpStatus.CONFLICT,
                        "sandbox-not-ready",
                        "沙箱当前状态不能执行该操作。")));

        client.post()
                .uri("/sandboxes/{id}/-/exec", SANDBOX_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"command":"pwd","workdir":".","timeoutSeconds":30,"riskLevel":"low"}
                        """)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CONFLICT);

        verify(audits).failure(
                any(), eq("EXEC_SANDBOX"), eq(SANDBOX_ID.toString()), eq("sandbox-not-ready"));
    }

    @Test
    void listsOperationsWithTheDecodedCursorAndLimit() {
        var position = new CreationCursor.Position(NOW, UUID.randomUUID());
        var cursor = CreationCursor.encode(position.sortTime(), position.id());
        var page = new SandboxOperationPage(
                List.of(operation(SandboxOperation.Status.SUCCEEDED)), "next", 1);
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(sandboxes.operations(ALICE, SANDBOX_ID, position, 1)).thenReturn(Mono.just(page));

        client.get()
                .uri(uri -> uri.path("/sandboxes/{id}/operations")
                        .queryParam("cursor", cursor)
                        .queryParam("limit", 1)
                        .build(SANDBOX_ID))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items[0].operationType")
                .isEqualTo("exec")
                .jsonPath("$.nextCursor")
                .isEqualTo("next");
    }

    @Test
    void viewsOneOperation() {
        var operation = operation(SandboxOperation.Status.SUCCEEDED);
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(sandboxes.operation(ALICE, SANDBOX_ID, operation.id()))
                .thenReturn(Mono.just(operation));

        client.get()
                .uri("/sandboxes/{id}/operations/{operationId}", SANDBOX_ID, operation.id())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(operation.id().toString());
    }

    @Test
    void rejectsAMalformedOperationIdBeforeResolvingIdentity() {
        client.get()
                .uri("/sandboxes/{id}/operations/not-a-uuid", SANDBOX_ID)
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(identity, sandboxes, events, audits);
    }

    @Test
    void resumesAStoppedSandboxAndAuditsTheTransition() {
        var stopped = sandbox(Sandbox.Status.STOPPED);
        var ready = sandbox(Sandbox.Status.READY);
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(sandboxes.view(ALICE, SANDBOX_ID)).thenReturn(Mono.just(stopped));
        when(sandboxes.resume(ALICE, SANDBOX_ID)).thenReturn(Mono.just(ready));

        client.post()
                .uri("/sandboxes/{id}/-/resume", SANDBOX_ID)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("ready");

        verify(audits).record(
                any(),
                eq("RESUME_SANDBOX"),
                eq(stopped),
                eq(true),
                anyMap());
    }

    @Test
    void uploadsAMultipartFileAndAuditsOnlyItsMetadata() {
        var content = "hello".getBytes(StandardCharsets.UTF_8);
        var result = new SandboxFileResult(
                "/home/pudans/workspace/notes/a.txt",
                content.length,
                Digests.sha256Hex(content));
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(sandboxes.view(ALICE, SANDBOX_ID))
                .thenReturn(Mono.just(sandbox(Sandbox.Status.READY)));
        when(sandboxes.upload(eq(ALICE), eq(SANDBOX_ID), eq("notes/a.txt"), any(byte[].class)))
                .thenReturn(Mono.just(result));

        var multipart = new MultipartBodyBuilder();
        multipart.part("path", "notes/a.txt");
        multipart.part("file", new ByteArrayResource(content))
                .filename("a.txt")
                .contentType(MediaType.TEXT_PLAIN);

        client.post()
                .uri("/sandboxes/{id}/-/upload", SANDBOX_ID)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipart.build()))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.path")
                .isEqualTo(result.path())
                .jsonPath("$.sizeBytes")
                .isEqualTo(content.length)
                .jsonPath("$.sha256")
                .isEqualTo(result.sha256());

        verify(audits).record(
                any(),
                eq("UPLOAD_SANDBOX_FILE"),
                any(Sandbox.class),
                eq(true),
                anyMap());
    }

    @Test
    void rejectsAnOversizedUploadBeforeResolvingIdentity() {
        properties.setMaxFileBytes(1_024);
        var multipart = new MultipartBodyBuilder();
        multipart.part("path", "notes/a.txt");
        multipart.part("file", new ByteArrayResource(new byte[1_025]))
                .filename("a.txt")
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        client.post()
                .uri("/sandboxes/{id}/-/upload", SANDBOX_ID)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipart.build()))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);

        verifyNoInteractions(identity, sandboxes, events, audits);
    }

    @Test
    void downloadsAFileWithSafeHeaders() {
        var content = "hello".getBytes(StandardCharsets.UTF_8);
        var result = new Sandboxes.DownloadResult(
                content, Digests.sha256Hex(content), "a.txt");
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(sandboxes.view(ALICE, SANDBOX_ID))
                .thenReturn(Mono.just(sandbox(Sandbox.Status.READY)));
        when(sandboxes.download(ALICE, SANDBOX_ID, "notes/a.txt"))
                .thenReturn(Mono.just(result));

        client.get()
                .uri(uri -> uri.path("/sandboxes/{id}/-/download")
                        .queryParam("path", "notes/a.txt")
                        .build(SANDBOX_ID))
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .expectHeader()
                .value(
                        HttpHeaders.CONTENT_DISPOSITION,
                        value -> assertThat(value).contains("a.txt"))
                .expectHeader()
                .contentLength(content.length)
                .expectBody(byte[].class)
                .isEqualTo(content);

        verify(audits).record(
                any(),
                eq("DOWNLOAD_SANDBOX_FILE"),
                any(Sandbox.class),
                eq(true),
                anyMap());
    }

    @Test
    void rejectsADownloadWithoutAPathBeforeResolvingIdentity() {
        client.get()
                .uri("/sandboxes/{id}/-/download", SANDBOX_ID)
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(identity, sandboxes, events, audits);
    }

    @Test
    void createsASnapshotAndReturnsCreated() {
        var snapshot = new SandboxSnapshot(
                UUID.randomUUID(),
                SANDBOX_ID,
                "agentrun",
                "snapshot-1",
                "s3",
                "sandbox_snapshot",
                "snapshot-policy",
                "sandbox/snapshot.tar",
                42L,
                "checksum",
                SandboxSnapshot.Status.READY,
                NOW,
                NOW.plusSeconds(60));
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(sandboxes.view(ALICE, SANDBOX_ID))
                .thenReturn(Mono.just(sandbox(Sandbox.Status.READY)));
        when(sandboxes.snapshot(ALICE, SANDBOX_ID)).thenReturn(Mono.just(snapshot));

        client.post()
                .uri("/sandboxes/{id}/-/snapshot", SANDBOX_ID)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.snapshotId")
                .isEqualTo("snapshot-1");
    }

    @Test
    void stopsASandbox() {
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(sandboxes.stop(ALICE, SANDBOX_ID))
                .thenReturn(Mono.just(sandbox(Sandbox.Status.STOPPED)));

        client.post()
                .uri("/sandboxes/{id}/-/stop", SANDBOX_ID)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("stopped");
    }

    @Test
    void streamsReplayedEventsWithTheirRedisId() {
        var sandbox = sandbox(Sandbox.Status.READY);
        var event = new SandboxEvent(
                "1700000000000-0",
                3,
                SANDBOX_ID,
                SandboxEvents.READY,
                Map.of("sandboxId", SANDBOX_ID),
                NOW);
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(sandboxes.view(ALICE, SANDBOX_ID)).thenReturn(Mono.just(sandbox));
        when(events.stream(sandbox, "1600000000000-0")).thenReturn(Flux.just(event));

        client.get()
                .uri("/sandboxes/{id}/events", SANDBOX_ID)
                .header("Last-Event-ID", "1600000000000-0")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectHeader()
                .valueEquals("Cache-Control", "no-cache")
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("id:1700000000000-0")
                        .contains("event:sandbox.ready")
                        .contains("\"type\":\"sandbox.ready\""));
    }

    @Test
    void rejectsAMalformedLastEventIdBeforeResolvingIdentity() {
        client.get()
                .uri("/sandboxes/{id}/events", SANDBOX_ID)
                .header("Last-Event-ID", "not-a-stream-id")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(identity, sandboxes, events, audits);
    }

    @Test
    void deletesASandboxAndKeepsTheAuditRecord() {
        var sandbox = sandbox(Sandbox.Status.READY);
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(sandboxes.view(ALICE, SANDBOX_ID)).thenReturn(Mono.just(sandbox));
        when(sandboxes.delete(ALICE, SANDBOX_ID)).thenReturn(Mono.empty());

        client.delete()
                .uri("/sandboxes/{id}", SANDBOX_ID)
                .exchange()
                .expectStatus()
                .isNoContent();

        verify(audits).record(
                any(),
                eq("DELETE_SANDBOX"),
                eq(sandbox),
                eq(true),
                anyMap());
    }

    private static String createRequest() {
        return """
                {"provider":"agentrun","agentSessionId":"%s","runId":null,
                 "template":null,"region":null,"ttlSeconds":1800}
                """.formatted(UUID.randomUUID());
    }

    private static Sandbox sandbox(Sandbox.Status status) {
        return new Sandbox(
                SANDBOX_ID,
                "alice",
                "workspace-a",
                UUID.randomUUID(),
                null,
                "agentrun",
                "default-template",
                "cn-shanghai",
                status,
                "/home/pudans/workspace",
                NOW.plusSeconds(1_800),
                NOW,
                NOW,
                NOW,
                status.terminal() ? NOW : null,
                null,
                null);
    }

    private static SandboxOperation operation(SandboxOperation.Status status) {
        return new SandboxOperation(
                UUID.randomUUID(),
                SANDBOX_ID,
                "exec",
                "pwd",
                status,
                0,
                null,
                null,
                false,
                null,
                30,
                NOW,
                NOW.plusSeconds(1),
                null,
                null);
    }
}
