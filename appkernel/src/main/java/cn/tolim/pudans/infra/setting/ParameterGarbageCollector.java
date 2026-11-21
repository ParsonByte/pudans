package cn.tolim.pudans.infra.setting;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.tolim.pudans.infra.utils.PudansTime;
import cn.tolim.pudans.security.authentication.rememberme.RememberTokenCleaner;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParameterGarbageCollector {

    private static final String DEFAULT_GC_CRON = "0 0 3 * * *";

    private static final Duration TEMP_FILE_TTL = Duration.ofHours(24);

    private static final int TEMP_SCAN_DEPTH = 2;

    private final ParameterGroups parameterGroups;

    private final ParameterSettingTempPathResolver tempPathResolver;

    private final RememberTokenCleaner rememberTokenCleaner;

    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();

    private ScheduledFuture<?> currentTask;

    @PostConstruct
    void startScheduler() {
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("parameter-gc-");
        taskScheduler.initialize();
    }

    @PreDestroy
    void stopScheduler() {
        taskScheduler.shutdown();
    }

    @EventListener
    void onExtensionInitialized(ExtensionInitializedEvent ignored) {
        reschedule();
    }

    @EventListener
    void onParameterSettingChanged(ParameterSettingChangedEvent event) {
        var previous = event.getOldData().get("server");
        var current = event.getNewData().get("server");
        if (java.util.Objects.equals(previous, current)) {
            return;
        }
        reschedule();
    }

    void reschedule() {
        parameterGroups.getServer().subscribe(
                server -> schedule(server.getGcCron()),
                error -> {
                    log.warn("无法读取垃圾回收 cron 表达式，使用默认值", error);
                    schedule(DEFAULT_GC_CRON);
                });
    }

    private synchronized void schedule(String cron) {
        if (currentTask != null) {
            currentTask.cancel(false);
        }
        var expression = StringUtils.hasText(cron) && CronExpression.isValidExpression(cron)
                ? cron
                : DEFAULT_GC_CRON;
        if (!expression.equals(cron)) {
            log.warn("无效的垃圾回收 cron 表达式 [{}]，使用 [{}]", cron, DEFAULT_GC_CRON);
        }
        currentTask = taskScheduler.schedule(this::run, gcTrigger(expression));
        log.info("参数垃圾回收已按 cron 表达式 [{}] 调度", expression);
    }

    // 回收 cron 以北京时间为基准，默认表达式为每天 03:00
    static CronTrigger gcTrigger(String expression) {
        return new CronTrigger(expression, PudansTime.BEIJING);
    }

    void run() {
        Schedulers.boundedElastic().schedule(() -> {
            try {
                cleanTempFiles();
            } catch (RuntimeException e) {
                log.error("无法清理过期的临时文件", e);
            }
            try {
                rememberTokenCleaner.cleanUpExpiredTokens();
            } catch (RuntimeException e) {
                log.error("无法清理过期的记住我令牌", e);
            }
        });
    }

    int cleanTempFiles() {
        var directory = tempPathResolver.resolve().blockOptional(Duration.ofSeconds(10)).orElse(null);
        if (directory == null || Files.notExists(directory)) {
            return 0;
        }
        var threshold = Instant.now().minus(TEMP_FILE_TTL);
        var deleted = new int[] {0};
        try (var paths = Files.walk(directory, TEMP_SCAN_DEPTH)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> olderThan(path, threshold))
                    .forEach(path -> delete(path, deleted));
        } catch (IOException e) {
            log.warn("无法扫描临时目录 [{}]", directory, e);
        }
        if (deleted[0] > 0) {
            log.info("已删除 {} 个过期临时文件，来源目录 [{}]", deleted[0], directory);
        }
        return deleted[0];
    }

    private static boolean olderThan(Path path, Instant threshold) {
        try {
            return Files.getLastModifiedTime(path).toInstant().isBefore(threshold);
        } catch (IOException e) {
            return false;
        }
    }

    private static void delete(Path path, int[] deleted) {
        try {
            if (Files.deleteIfExists(path)) {
                deleted[0]++;
            }
        } catch (IOException e) {
            log.warn("无法删除临时文件 [{}]", path, e);
        }
    }
}
