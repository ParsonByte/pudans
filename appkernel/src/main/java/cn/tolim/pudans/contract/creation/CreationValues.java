package cn.tolim.pudans.contract.creation;

import java.util.Set;

public final class CreationValues {

    public static final String ASSET_STATUS_ACTIVE = "active";

    public static final Set<String> ASSET_KINDS = Set.of("image", "video", "audio", "document", "other");

    public static final Set<String> ASSET_SOURCES = Set.of("creation", "chat", "upload");

    public static final String CONTENT_STATUS_DRAFT = "draft";

    public static final String CONTENT_STATUS_APPROVED = "approved";

    public static final String CONTENT_STATUS_REJECTED = "rejected";

    public static final String CONTENT_STATUS_PUBLISHED = "published";

    public static final String VISIBILITY_PRIVATE = "private";

    public static final String VISIBILITY_PUBLIC = "public";

    public static final String INTERACTION_LIKE = "like";

    public static final String INTERACTION_FAVORITE = "favorite";

    public static final String INTERACTION_SHARE = "share";

    public static final String INTERACTION_REPORT = "report";

    public static final String SUBMISSION_STATUS_SUBMITTED = "submitted";

    private CreationValues() {}
}
