package cn.tolim.pudans.iam.communal.entity.setting;

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

import java.io.Serial;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Entity
@Accessors(chain = true)
@Table(name = "pudans_setting")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class SettingEntity extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 5983857137670090984L;
    // name
    @Column(name = "name_")
    private String name;
    // value
    @Column(name = "value_")
    private String value;
    // desc
    @Column(name = "desc_")
    private String desc;

}
