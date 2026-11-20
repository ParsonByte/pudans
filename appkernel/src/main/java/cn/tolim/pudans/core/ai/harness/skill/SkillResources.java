package cn.tolim.pudans.core.ai.harness.skill;

import java.util.List;
import java.util.Optional;

public interface SkillResources {

    Optional<String> read(String relativePath);

    Optional<byte[]> readBinary(String relativePath);

    List<String> list();

    static SkillResources empty() {
        return EmptySkillResources.INSTANCE;
    }
}

final class EmptySkillResources implements SkillResources {

    static final EmptySkillResources INSTANCE = new EmptySkillResources();

    private EmptySkillResources() {}

    @Override
    public Optional<String> read(String relativePath) {
        return Optional.empty();
    }

    @Override
    public Optional<byte[]> readBinary(String relativePath) {
        return Optional.empty();
    }

    @Override
    public List<String> list() {
        return List.of();
    }
}