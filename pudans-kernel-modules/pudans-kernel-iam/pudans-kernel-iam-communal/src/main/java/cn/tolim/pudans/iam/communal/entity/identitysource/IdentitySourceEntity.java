package cn.tolim.pudans.iam.communal.entity.identitysource;

import cn.tolim.pudans.iam.communal.entity.identitysource.config.JobConfig;
import cn.tolim.pudans.iam.communal.entity.identitysource.config.StrategyConfig;
import cn.tolim.pudans.iam.communal.enums.identitysource.IdentitySourceProvider;
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
@Table(name = "pudans_identity_source")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class IdentitySourceEntity extends BaseEntity {

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
    @Column(name = "provider_")
    private IdentitySourceProvider provider;

    // Base configuration JSON
    @Column(name = "basic_config")
    @JdbcTypeCode(SqlTypes.JSON)
    private String basicConfig;

    // Sync policy JSON
    @Column(name = "strategy_config")
    @JdbcTypeCode(SqlTypes.JSON)
    private StrategyConfig strategyConfig;

    // Schedule JSON
    @Column(name = "job_config")
    @JdbcTypeCode(SqlTypes.JSON)
    private JobConfig jobConfig;

    // Enabled
    @Column(name = "is_enabled")
    private Boolean enabled;

    // Configured
    @Column(name = "is_configured")
    private Boolean configured;

}
