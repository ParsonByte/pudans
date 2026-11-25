package cn.tolim.pudans.contract.creation.dto;

import cn.tolim.pudans.contract.creation.ActivitySubmission;
import java.util.List;

public record ActivitySubmissionPage(List<ActivitySubmission> items, String emptyState) {}
