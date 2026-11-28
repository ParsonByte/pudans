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
import org.hibernate.annotations.SoftDelete;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Accessors(chain = true)
@Entity
@Table(name = "pudans_user_group_member")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class UserGroupMemberEntity extends BaseEntity {
    // Group ID
    @Column(name = "group_id")
    private String groupId;
    // User ID
    @Column(name = "user_id")
    private String userId;
}
