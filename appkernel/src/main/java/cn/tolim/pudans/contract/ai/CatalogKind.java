package cn.tolim.pudans.contract.ai;

import java.util.Arrays;
import java.util.Optional;

public enum CatalogKind {
    CREATION_MEDIA_MODE("creation_media_mode"),
    CREATION_OPERATION("creation_operation"),
    CREATION_PARAMETER_OPTION("creation_parameter_option"),
    UNDERSTANDING_DIMENSION("understanding_dimension"),
    MEMORY_TYPE("memory_type"),
    MEMORY_LAYER("memory_layer"),
    ASSET_KIND("asset_kind"),
    ASSET_SOURCE("asset_source"),
    SANDBOX_PROVIDER("sandbox_provider"),
    SANDBOX_TEMPLATE("sandbox_template"),
    SANDBOX_RESOURCE_PROFILE("sandbox_resource_profile"),
    CHAT_ANSWER_MODE("chat_answer_mode"),
    CHAT_REASONING_LEVEL("chat_reasoning_level"),
    EXPLORE_CATEGORY("explore_category"),
    EXPLORE_FILTER("explore_filter");

    private final String code;

    CatalogKind(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<CatalogKind> fromCode(String value) {
        return Arrays.stream(values())
                .filter(kind -> kind.code.equals(value))
                .findFirst();
    }
}
