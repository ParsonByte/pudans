package cn.tolim.pudans.iam.communal.entity.app;

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
@Table(name = "pudans_app_account")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class AppAccountEntity extends BaseEntity {
    // Application ID
    @Column(name = "app_id")
    private String appId;

    // User ID
    @Column(name = "user_id")
    private String userId;

    // Account name
    @Column(name = "account_")
    private String account;

    // Account password
    @Column(name = "password_")
    private String password;

    // Default
    @Column(name = "default_")
    private Boolean defaulted;
}
