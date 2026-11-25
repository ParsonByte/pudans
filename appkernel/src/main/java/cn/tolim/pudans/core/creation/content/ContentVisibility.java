package cn.tolim.pudans.core.creation.content;

import cn.tolim.pudans.contract.creation.CreationValues;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Instant;

final class ContentVisibility {

    private ContentVisibility() {}

    static boolean owns(String ownerId, String workspaceId, RequestIdentity identity) {
        return identity.ownerId().equals(ownerId) && identity.workspaceId().equals(workspaceId);
    }

    static boolean isPublicPublished(String status, String visibility, Instant publishedAt) {
        return CreationValues.CONTENT_STATUS_PUBLISHED.equals(status)
                && CreationValues.VISIBILITY_PUBLIC.equals(visibility)
                && publishedAt != null;
    }

    static boolean isPublicPublished(String status, String visibility) {
        return CreationValues.CONTENT_STATUS_PUBLISHED.equals(status)
                && CreationValues.VISIBILITY_PUBLIC.equals(visibility);
    }

    static boolean isPublicActive(String status, String visibility) {
        return CreationValues.ASSET_STATUS_ACTIVE.equals(status)
                && CreationValues.VISIBILITY_PUBLIC.equals(visibility);
    }
}
