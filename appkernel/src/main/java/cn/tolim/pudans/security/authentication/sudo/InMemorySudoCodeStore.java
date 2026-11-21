package cn.tolim.pudans.security.authentication.sudo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;


@Component
class InMemorySudoCodeStore {

    static final int CODE_LENGTH = 6;
    static final Duration CODE_TTL = Duration.ofMinutes(10);

    private final ConcurrentHashMap<String, CodeEntry> codeStore = new ConcurrentHashMap<>();

    private Clock clock = Clock.systemUTC();

    // 替换时钟以便测试验证码过期逻辑
    void setClock(Clock clock) {
        Assert.notNull(clock, "时钟不能为空");
        this.clock = clock;
    }

    // 为指定键生成一次性验证码并异步执行
    Mono<String> generate(String key) {
        Assert.state(StringUtils.isNotBlank(key), "键不能为空");
        return Mono.fromCallable(() -> generateCode(key)).subscribeOn(Schedulers.boundedElastic());
    }

    // 校验并消费验证码，确保只能使用一次
    Mono<Void> verify(String key, String code) {
        Assert.state(StringUtils.isNotBlank(key), "键不能为空");
        Assert.state(StringUtils.isNotBlank(code), "验证码不能为空");
        return Mono.fromRunnable(() -> verifyCode(key, code));
    }

    // 生成前清理过期条目，避免内存持续增长
    private String generateCode(String key) {
        codeStore.values().removeIf(entry -> entry.isExpired(clock));
        var code = RandomStringUtils.secure().nextNumeric(CODE_LENGTH);
        codeStore.put(key, new CodeEntry(code, clock.instant()));
        return code;
    }

    // 成功后原子移除，防止同一验证码被重复使用
    private void verifyCode(String key, String code) {
        var entry = codeStore.get(key);
        if (entry == null || entry.isExpired(clock) || !entry.matches(code)) {
            throw new SudoVerificationFailedException();
        }

        if (!codeStore.remove(key, entry)) {
            throw new SudoVerificationFailedException();
        }
    }

    record CodeEntry(String code, Instant createdAt) {

        // 根据外部时钟判断验证码是否过期
        boolean isExpired(Clock clock) {
            return createdAt.plus(CODE_TTL).isBefore(clock.instant());
        }

        // 使用常量时间比较，降低时序攻击风险
        boolean matches(String submittedCode) {
            return MessageDigest.isEqual(
                    code.getBytes(StandardCharsets.UTF_8), submittedCode.getBytes(StandardCharsets.UTF_8));
        }
    }
}
