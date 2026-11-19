package cn.tolim.pudans.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicy;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicyType;

class StoragePolicyPathGeneratorTest {

    private final StoragePolicyPathGenerator generator = new StoragePolicyPathGenerator();

    private final Instant now = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void rendersOwnerPathAndOriginName() {
        var policy = policy("uploads/{uid}/{path}", "{randomkey8}_{originname}");

        var key = generator.generate(
                policy, new StoragePathContext("alice", "chat/2026", "report.pdf", now));

        assertThat(key).startsWith("uploads/alice/chat/2026/");
        assertThat(key).endsWith("_report.pdf");
        var fileName = key.substring(key.lastIndexOf('/') + 1);
        assertThat(fileName).hasSize(8 + 1 + "report.pdf".length());
    }

    @Test
    void sanitizesOriginNameAndPath() {
        var policy = policy("uploads/{path}", "{originname}");

        var key = generator.generate(
                policy, new StoragePathContext(null, "/chat/../secret", "../evil.txt", now));

        assertThat(key).isEqualTo("uploads/chat/secret/.._evil.txt");
    }

    @Test
    void rendersDateVariables() {
        var policy = policy("uploads/{date}", "{year}-{month}-{day}_{randomkey8}_{originname}");

        var key = generator.generate(policy, new StoragePathContext(null, "", "a.txt", now));

        assertThat(key).startsWith("uploads/2026");
        assertThat(key).contains("2026-01-02_");
    }

    @Test
    void validatesUnknownVariables() {
        var errors = generator.validateRules("{unknown}/{path}", "{randomkey8}_{originname}");

        assertThat(errors).anyMatch(error -> error.contains("{unknown}"));
    }

    @Test
    void rejectsPathVariableInFileNameRule() {
        var errors = generator.validateRules("uploads/{path}", "{path}_{originname}");

        assertThat(errors).anyMatch(error -> error.contains("{path}"));
    }

    @Test
    void requiresAUniqueVariable() {
        var errors = generator.validateRules("uploads/{path}", "{originname}");

        assertThat(errors).anyMatch(error -> error.contains("随机变量"));
    }

    @Test
    void rejectsParentSegments() {
        var errors = generator.validateRules("uploads/../{path}", "{randomkey8}_{originname}");

        assertThat(errors).anyMatch(error -> error.contains("上级目录"));
    }

    @Test
    void preservesDollarSignsInFileNames() {
        var policy = policy("uploads/{path}", "{randomkey8}_{originname}");

        var key = generator.generate(
                policy, new StoragePathContext(null, "", "Report$2024.pdf", now));

        assertThat(key).endsWith("_Report$2024.pdf");
    }

    @Test
    void rendersEpochNanoseconds() {
        var policy = policy("uploads", "{timestamp_nano}");

        var key = generator.generate(policy, new StoragePathContext(null, "", "a.txt", now));

        var expected = now.getEpochSecond() * 1_000_000_000L + now.getNano();
        assertThat(key).isEqualTo("uploads/" + expected);
    }

    @Test
    void keepsTimestampKeysUniqueWithinTheSameSecond() {
        var policy = policy("uploads", "{timestamp}_{originname}");
        var later = now.plusMillis(1);

        var first = generator.generate(policy, new StoragePathContext(null, "", "a.txt", now));
        var second = generator.generate(policy, new StoragePathContext(null, "", "a.txt", later));

        assertThat(later.getEpochSecond()).isEqualTo(now.getEpochSecond());
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rejectsRulesThatOnlyUseRandomNumbers() {
        var errors = generator.validateRules("uploads/{path}", "{randomnum2}_{originname}");

        assertThat(errors).anyMatch(error -> error.contains("随机变量"));
    }

    private static StoragePolicy policy(String dir, String file) {
        var policy = new StoragePolicy();
        policy.getSpec().setDisplayName("test");
        policy.getSpec().setType(StoragePolicyType.LOCAL);
        policy.getSpec().setDirNameRule(dir);
        policy.getSpec().setFileNameRule(file);
        return policy;
    }
}
