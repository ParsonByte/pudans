package cn.tolim.pudans.iam.communal.entity.account;

import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.Hibernate;
import org.hibernate.annotations.SoftDelete;

import java.util.Objects;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Accessors(chain = true)
@Entity
@Table(name = "pudans_organization_member")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class OrganizationMemberEntity extends BaseEntity {
    // Organization ID
    @Column(name = "org_id")
    private String orgId;

    // User ID
    @Column(name = "user_id")
    private String userId;

    public OrganizationMemberEntity() {
    }

    public OrganizationMemberEntity(String orgId, String userId) {
        this.orgId = orgId;
        this.userId = userId;
    }

    public OrganizationMemberEntity(String id, String orgId, String userId) {
        super.setId(id);
        this.orgId = orgId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        OrganizationMemberEntity that = (OrganizationMemberEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
