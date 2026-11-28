package cn.tolim.pudans.iam.communal.entity.account.po;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;


@Getter
@Setter
public class OrganizationMemberPO implements Serializable {
    @Serial
    private static final long serialVersionUID = -150631305460653395L;

    // Primary key ID
    private String id;

    // User ID
    private String userId;

    // Organization ID
    private String orgId;

    // Organization name
    private String orgName;

    // Display path
    private String displayPath;

    public OrganizationMemberPO(String id, String userId, String orgId, String orgName,
                                String displayPath) {
        this.id = String.valueOf(id);
        this.userId = String.valueOf(userId);
        this.orgId = orgId;
        this.orgName = orgName;
        this.displayPath = displayPath;
    }
}
