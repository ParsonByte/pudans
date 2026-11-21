package cn.tolim.pudans.core.live.recorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import cn.tolim.pudans.core.infra.properties.LiveProperties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LiveRecorderAdmissionTest {

    @Test
    void admitsUntilConcurrencyAndQueueAreFull() throws InterruptedException {
        var properties = new LiveProperties();
        properties.getRecorder().setMaxConcurrent(1);
        properties.getRecorder().setQueueCapacity(1);
        var admission = new LiveRecorderAdmission(properties);
        try {
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var first = admission.admit("rec-a", () -> {
                started.countDown();
                await(release);
            });
            assertThat(first).isEqualTo(LiveRecorderAdmission.Admission.ADMITTED);
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

            assertThat(admission.admit("rec-b", () -> {})).isEqualTo(LiveRecorderAdmission.Admission.QUEUED);
            assertThat(admission.admit("rec-c", () -> {})).isEqualTo(LiveRecorderAdmission.Admission.REJECTED);
            assertThat(admission.admit("rec-a", () -> {})).isEqualTo(LiveRecorderAdmission.Admission.REJECTED);
            assertThat(admission.activeCount()).isEqualTo(1);
            assertThat(admission.queuedCount()).isEqualTo(1);

            assertThat(admission.cancel("rec-b")).isTrue();
            assertThat(admission.queuedCount()).isZero();

            release.countDown();
            awaitIdle(admission);
            assertThat(admission.activeCount()).isZero();
            assertThat(admission.admit("rec-d", () -> {}))
                    .isEqualTo(LiveRecorderAdmission.Admission.ADMITTED);
        } finally {
            admission.shutdown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitIdle(LiveRecorderAdmission admission) {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (admission.activeCount() == 0) {
                return;
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                fail("等待录制任务结束被中断");
            }
        }
        fail("录制任务未在预期时间内结束");
    }
}
