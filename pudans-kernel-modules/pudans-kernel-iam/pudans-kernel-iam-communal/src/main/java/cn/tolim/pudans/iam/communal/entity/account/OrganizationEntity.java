package cn.tolim.pudans.iam.communal.entity.account;

import cn.tolim.pudans.iam.communal.enums.account.OrganizationType;
import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.Hibernate;
import org.hibernate.annotations.SoftDelete;

import java.io.Serial;
import java.util.Objects;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Entity
@Table(name = "pudans_organization")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class OrganizationEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 8143944323232082295L;

    // Organization name
    @Column(name = "name_")
    private String name;

    // Organization code
    @Column(name = "code_")
    private String code;

    // Type
    @Column(name = "type_")
    private OrganizationType type;

    // Parent ID
    @Column(name = "parent_id")
    private String parentId;

    // Path enumeration ID
    @Column(name = "path_")
    private String path;

    // Path display name
    @Column(name = "display_path")
    private String displayPath;

    // External ID
    @Column(name = "external_id")
    private String externalId;

    // Data source
    @Column(name = "data_origin")
    private String dataOrigin;

    // Identity source ID
    @Column(name = "identity_source_id")
    private String identitySourceId;

    // Sort
    @Column(name = "order_")
    private Long order;

    // Leaf node
    @Column(name = "is_leaf")
    private Boolean leaf;

    // Enabled
    @Column(name = "is_enabled")
    private Boolean enabled;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        OrganizationEntity entity = (OrganizationEntity) o;
        return getId() != null && Objects.equals(getId(), entity.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
