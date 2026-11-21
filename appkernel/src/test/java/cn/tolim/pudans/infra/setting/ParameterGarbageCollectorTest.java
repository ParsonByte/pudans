package cn.tolim.pudans.infra.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.security.authentication.rememberme.RememberTokenCleaner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.support.SimpleTriggerContext;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class ParameterGarbageCollectorTest {

    @Mock
    ParameterGroups parameterGroups;

    @Mock
    ParameterSettingTempPathResolver tempPathResolver;

    @Mock
    RememberTokenCleaner rememberTokenCleaner;

    @TempDir
    Path tempDir;

    @Test
    void deletesOnlyExpiredTemporaryFiles() throws IOException {
        var expired = Files.writeString(tempDir.resolve("expired.tmp"), "old");
        var fresh = Files.writeString(tempDir.resolve("fresh.tmp"), "new");
        Files.setLastModifiedTime(expired, FileTime.from(Instant.now().minusSeconds(2 * 24 * 3600)));
        when(tempPathResolver.resolve()).thenReturn(Mono.just(tempDir));

        var collector = new ParameterGarbageCollector(
                parameterGroups, tempPathResolver, rememberTokenCleaner);
        var deleted = collector.cleanTempFiles();

        assertThat(deleted).isEqualTo(1);
        assertThat(Files.exists(expired)).isFalse();
        assertThat(Files.exists(fresh)).isTrue();
    }

    @Test
    void schedulesTheDailyGcAtBeijingThree() {
        var original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            var trigger = ParameterGarbageCollector.gcTrigger("0 0 3 * * *");
            var context = new SimpleTriggerContext();
            var now = Instant.parse("2026-11-19T16:00:00Z");
            context.update(now, now, now);

            assertThat(trigger.nextExecution(context))
                    .isEqualTo(Instant.parse("2026-11-19T19:00:00Z"));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void missingTemporaryDirectoryIsIgnored() {
        when(tempPathResolver.resolve()).thenReturn(Mono.just(tempDir.resolve("missing")));

        var collector = new ParameterGarbageCollector(
                parameterGroups, tempPathResolver, rememberTokenCleaner);

        assertThat(collector.cleanTempFiles()).isZero();
    }
}
