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

import java.io.Serial;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Accessors(chain = true)
@Entity
@Table(name = "pudans_user_group")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class UserGroupEntity extends BaseEntity {

    public static final String CODE_FIELD_NAME = "code";
    public static final String NAME_FIELD_NAME = "name";
    @Serial
    private static final long serialVersionUID = -2619231849746900857L;
    // Username
    @Column(name = "name_")
    private String name;

    // Password
    @Column(name = "code_")
    private String code;
}
