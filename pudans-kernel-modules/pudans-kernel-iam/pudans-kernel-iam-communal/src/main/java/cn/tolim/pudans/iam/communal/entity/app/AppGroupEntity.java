package cn.tolim.pudans.iam.communal.entity.app;

import cn.tolim.pudans.iam.communal.enums.app.AppGroupType;
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
@Table(name = "pudans_app_group")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class AppGroupEntity extends BaseEntity {

    // Group name
    @Column(name = "name_")
    private String name;

    // Group code
    @Column(name = "code_")
    private String code;

    // Group type
    @Column(name = "type_")
    private AppGroupType type;
}
