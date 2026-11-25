package cn.tolim.pudans.contract.creation.dto;

import cn.tolim.pudans.contract.creation.Subject;
import java.time.Instant;
import java.util.List;

public record SubjectPage(
        List<Subject> items,
        String nextCursor,
        int limit,
        String emptyState,
        Instant generatedAt) {}
