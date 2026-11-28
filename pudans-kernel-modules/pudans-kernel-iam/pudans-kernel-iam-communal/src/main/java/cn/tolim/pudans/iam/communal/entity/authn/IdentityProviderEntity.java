package cn.tolim.pudans.iam.communal.entity.authn;

import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.type.SqlTypes;

import java.io.Serial;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Entity
@Accessors(chain = true)
@Table(name = "pudans_identity_provider")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class IdentityProviderEntity extends BaseEntity {

    public static final String CATEGORY_FIELD_NAME = "category";
    public static final String NAME_FIELD_NAME = "name";
    @Serial
    private static final long serialVersionUID = -7936931011805155568L;
    // Name
    @Column(name = "name_")
    private String name;

    // Unique code is immutable
    @Column(name = "code_")
    private String code;

    // Platform
    @Column(name = "type_")
    private String type;

    // Category
    @Column(name = "category_")
    private String category;

    // Configuration JSON
    @Column(name = "config_")
    @JdbcTypeCode(SqlTypes.JSON)
    private String config;

    // Enabled
    @Column(name = "is_enabled")
    private Boolean enabled;

    // Visible
    @Column(name = "is_displayed")
    private Boolean displayed;

}
