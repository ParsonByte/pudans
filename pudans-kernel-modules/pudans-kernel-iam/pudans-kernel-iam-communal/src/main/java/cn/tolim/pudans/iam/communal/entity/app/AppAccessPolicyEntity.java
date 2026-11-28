package cn.tolim.pudans.iam.communal.entity.app;

import cn.tolim.pudans.iam.communal.enums.app.AppPolicySubjectType;
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
@Entity
@Accessors(chain = true)
@Table(name = "pudans_app_access_policy")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class AppAccessPolicyEntity extends BaseEntity {
    // Application ID
    @Column(name = "app_id")
    private String appId;

    // Principal ID (user, group, organization)
    @Column(name = "subject_id")
    private String subjectId;

    // Principal type (user, group, organization)
    @Column(name = "subject_type")
    private AppPolicySubjectType subjectType;

    // Enabled
    @Column(name = "is_enabled")
    private Boolean enabled;
}
