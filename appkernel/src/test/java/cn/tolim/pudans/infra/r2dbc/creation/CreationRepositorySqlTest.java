package cn.tolim.pudans.infra.r2dbc.creation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.creation.Asset;
import cn.tolim.pudans.contract.creation.CreationValues;
import cn.tolim.pudans.contract.creation.Subject;
import cn.tolim.pudans.contract.creation.Template;
import cn.tolim.pudans.contract.creation.Work;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class CreationRepositorySqlTest {

    private static final RequestIdentity IDENTITY = new RequestIdentity("alice", "workspace-a");

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final String[] ASSET_JSON_ALIASES = {
        "settings_jsonb::text AS settings_json",
        "tags_jsonb::text AS tags_json",
        "metadata_jsonb::text AS metadata_json"
    };

    private static final String[] WORK_JSON_ALIASES = {
        "tags_jsonb::text AS tags_json", "metadata_jsonb::text AS metadata_json"
    };

    private static final String[] TEMPLATE_JSON_ALIASES = {
        "settings_jsonb::text AS settings_json",
        "parameter_schema_jsonb::text AS parameter_schema_json",
        "tags_jsonb::text AS tags_json",
        "metadata_jsonb::text AS metadata_json"
    };

    private static final String[] SUBJECT_JSON_ALIASES = {
        "tags_jsonb::text AS tags_json", "metadata_jsonb::text AS metadata_json"
    };

    @Mock
    DatabaseClient databaseClient;

    private final List<String> queries = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(databaseClient.sql(anyString())).thenAnswer(invocation -> {
            queries.add(invocation.getArgument(0));
            return executeSpec();
        });
    }

    @Test
    void assetQueriesReadJsonbAsText() {
        var repository = new AssetRepository(databaseClient);
        var id = UUID.randomUUID();

        assertSql(() -> repository.insert(asset()), ASSET_JSON_ALIASES);
        assertSql(() -> repository.findOwned(id, IDENTITY), ASSET_JSON_ALIASES);
        assertSql(() -> repository.findById(id), ASSET_JSON_ALIASES);
        assertSql(() -> repository.findByStorageFile("file-1", null, IDENTITY), ASSET_JSON_ALIASES);
        assertSql(() -> repository.list(IDENTITY, null, null, null, null, 20), ASSET_JSON_ALIASES);
    }

    @Test
    void workQueriesReadJsonbAsText() {
        var repository = new WorkRepository(databaseClient);
        var work = work();

        assertSql(() -> repository.insert(work), WORK_JSON_ALIASES);
        assertSql(() -> repository.findById(work.id()), WORK_JSON_ALIASES);
        assertSql(() -> repository.feed(null, null, null, null, null, 20), WORK_JSON_ALIASES);
        assertSql(() -> repository.related(work, 12), WORK_JSON_ALIASES);
    }

    @Test
    void templateQueriesReadJsonbAsText() {
        var repository = new TemplateRepository(databaseClient);
        var template = template();

        assertSql(() -> repository.findById(template.id()), TEMPLATE_JSON_ALIASES);
        assertSql(() -> repository.feed(null, null, 20), TEMPLATE_JSON_ALIASES);
    }

    @Test
    void subjectQueriesReadJsonbAsText() {
        var repository = new SubjectRepository(databaseClient);
        var subject = subject();

        assertSql(() -> repository.findById(subject.id()), SUBJECT_JSON_ALIASES);
        assertSql(
                () -> repository.listMine(
                        IDENTITY.ownerId(), IDENTITY.workspaceId(), null, 20),
                SUBJECT_JSON_ALIASES);
    }

    @Test
    void interactionRemovalAlwaysReturnsAnInactiveState() {
        var repository = new InteractionRepository(databaseClient);
        var workId = UUID.randomUUID();

        assertSql(
                () -> repository.removeLike(workId, IDENTITY.ownerId(), IDENTITY.workspaceId(), NOW),
                "RETURNING like_count AS counter, FALSE AS active");
        assertThat(queries.getLast()).doesNotContain("EXISTS");

        assertSql(
                () -> repository.removeFavorite(
                        workId, IDENTITY.ownerId(), IDENTITY.workspaceId(), NOW),
                "RETURNING favorite_count AS counter, FALSE AS active");
        assertThat(queries.getLast()).doesNotContain("EXISTS");
    }

    private void assertSql(Runnable operation, String... fragments) {
        var before = queries.size();
        operation.run();
        assertThat(queries).hasSize(before + 1);
        assertThat(queries.getLast()).contains(fragments);
    }

    private static DatabaseClient.GenericExecuteSpec executeSpec() {
        var spec = mock(DatabaseClient.GenericExecuteSpec.class);
        lenient().when(spec.bind(anyString(), any())).thenReturn(spec);
        lenient().when(spec.bindNull(anyString(), any(Class.class))).thenReturn(spec);
        lenient().when(spec.map(ArgumentMatchers.<BiFunction<Row, RowMetadata, Object>>any()))
                .thenReturn(new EmptyRows<>());
        return spec;
    }

    private record EmptyRows<T>() implements RowsFetchSpec<T> {

        @Override
        public Mono<T> one() {
            return Mono.empty();
        }

        @Override
        public Mono<T> first() {
            return Mono.empty();
        }

        @Override
        public Flux<T> all() {
            return Flux.empty();
        }
    }

    private static Asset asset() {
        return new Asset(
                UUID.randomUUID(),
                IDENTITY.ownerId(),
                IDENTITY.workspaceId(),
                "image",
                "creation",
                "file-1",
                null,
                null,
                "result",
                null,
                "prompt",
                Map.of(),
                "image/png",
                42L,
                null,
                null,
                null,
                List.of(),
                Map.of(),
                CreationValues.ASSET_STATUS_ACTIVE,
                NOW,
                NOW);
    }

    private static Work work() {
        return new Work(
                UUID.randomUUID(),
                IDENTITY.ownerId(),
                IDENTITY.workspaceId(),
                "work",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                Map.of(),
                CreationValues.CONTENT_STATUS_PUBLISHED,
                CreationValues.VISIBILITY_PUBLIC,
                0,
                0,
                0,
                0,
                0,
                NOW,
                NOW,
                NOW);
    }

    private static Template template() {
        return new Template(
                UUID.randomUUID(),
                IDENTITY.ownerId(),
                IDENTITY.workspaceId(),
                "template",
                "Template",
                null,
                "image",
                "prompt",
                Map.of(),
                Map.of(),
                null,
                List.of(),
                Map.of(),
                CreationValues.CONTENT_STATUS_PUBLISHED,
                CreationValues.VISIBILITY_PUBLIC,
                NOW,
                NOW,
                NOW);
    }

    private static Subject subject() {
        return new Subject(
                UUID.randomUUID(),
                IDENTITY.ownerId(),
                IDENTITY.workspaceId(),
                "subject",
                null,
                "character",
                null,
                List.of(),
                Map.of(),
                "active",
                CreationValues.VISIBILITY_PRIVATE,
                NOW,
                NOW);
    }
}
