package cn.tolim.pudans.iam.communal.entity.app;

import cn.tolim.pudans.iam.communal.enums.app.JwtBindingType;
import cn.tolim.pudans.iam.communal.enums.app.JwtIdTokenSubjectType;
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

import java.time.Duration;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Entity
@Accessors(chain = true)
@Table(name = "pudans_app_jwt_config")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class AppJwtConfigEntity extends BaseEntity {

    // APP ID
    @Column(name = "app_id")
    private String appId;

    // JWT SSO URL in the business system. During single sign-on, the system sends id_token information to this address with the parameter name id_token. The business system can use id_token and the public key to obtain user information. If login starts in the business system (SP), the system validates the service parameter on the SP login address when present. On success, the browser is redirected to this address with the id_token identity token.
    @Column(name = "redirect_url")
    private String redirectUrl;

    // URL to redirect to after a successful JWT SSO in the business system, usually a secondary menu or similar.
    // If set, this URL is passed as target_uri during JWT SSO.
    // If not set, a target_uri request parameter from SSO is forwarded. Optional.
    @Column(name = "target_link_url")
    private String targetLinkUrl;

    // Redirect method
    @Column(name = "binding_type")
    private JwtBindingType bindingType;

    // id_token sub type
    @Column(name = "id_token_subject_type")
    private JwtIdTokenSubjectType idTokenSubjectType;

    // Token expiration time
    @Column(name = "id_token_time_to_live")
    @JdbcTypeCode(SqlTypes.BIGINT)
    private Duration idTokenTimeToLive;
}
