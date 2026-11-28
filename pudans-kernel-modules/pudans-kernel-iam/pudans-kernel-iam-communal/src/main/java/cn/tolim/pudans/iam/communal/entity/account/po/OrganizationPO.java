package cn.tolim.pudans.iam.communal.entity.account.po;

import cn.tolim.pudans.iam.communal.enums.account.OrganizationType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;


@Data
@Slf4j
@NoArgsConstructor
public class OrganizationPO implements Serializable {
    @Serial
    private static final long serialVersionUID = -150631305460653395L;
    // Primary key ID
    private String id;
    // key
    private String name;

    // Code
    private String code;

    // Organization type
    private OrganizationType type;

    // Parent
    private String parentId;

    // path
    private String path;

    // Display path
    private String displayPath;

    // External ID
    private String externalId;

    // Source
    private String dataOrigin;

    // Identity source ID
    private String identitySourceId;

    // Sort
    private Long order;

    // Leaf node
    private Boolean leaf;

    // Enabled
    private Boolean enabled;

    // Remark
    private String remark;

    // Primary organization
    private Boolean primary;

    // Extension field
    private Map<String, Object> customFields;

    public OrganizationPO(String id, String name, String code, OrganizationType type,
                          String parentId, String path, String displayPath, String externalId,
                          String dataOrigin, String identitySourceId, Long order, Boolean leaf,
                          Boolean enabled, String remark) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.type = type;
        this.parentId = parentId;
        this.path = path;
        this.displayPath = displayPath;
        this.externalId = externalId;
        this.dataOrigin = dataOrigin;
        this.identitySourceId = identitySourceId;
        this.order = order;
        this.leaf = leaf;
        this.enabled = enabled;
        this.remark = remark;
    }
}
