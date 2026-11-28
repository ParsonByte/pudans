package cn.tolim.pudans.iam.communal.entity.app;

import cn.tolim.pudans.iam.communal.enums.app.AppProtocol;
import cn.tolim.pudans.iam.communal.enums.app.AppType;
import cn.tolim.pudans.iam.communal.enums.app.AuthorizationType;
import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.SoftDelete;

import java.util.List;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;
import static jakarta.persistence.FetchType.LAZY;


@Getter
@Setter
@ToString
@Entity
@Accessors(chain = true)
@Table(name = "pudans_app")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class AppEntity extends BaseEntity {

    // Application name
    @Column(name = "name_")
    private String name;

    // Unique code is immutable
    @Column(name = "code_")
    private String code;

    // Client ID
    @Column(name = "client_id")
    private String clientId;
    // Client secret
    @Column(name = "client_secret")
    private String clientSecret;

    // Template
    @Column(name = "template_")
    private String template;

    // Protocol
    @Column(name = "protocol_")
    private AppProtocol protocol;

    // Application type
    @Column(name = "type_")
    private AppType type;

    // Application icon
    @Column(name = "icon_")
    private String icon;

    // SSO login initiation URL
    @Column(name = "init_login_url")
    private String initLoginUrl;

    // SSO grant type
    @Column(name = "authorization_type")
    private AuthorizationType authorizationType;

    // Enabled
    @Column(name = "is_enabled")
    private Boolean enabled;

    // Configured
    @Column(name = "is_configured")
    private Boolean configured;

    @ToString.Exclude
    @OneToMany(mappedBy = "app", fetch = LAZY)
    private List<AppGroupAssociationEntity> groups;
}
