package cn.tolim.pudans.iam.communal.entity.app;

import cn.tolim.pudans.iam.communal.enums.app.FormEncryptType;
import cn.tolim.pudans.iam.communal.enums.app.FormSubmitType;
import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.List;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Entity
@Accessors(chain = true)
@Table(name = "pudans_app_form_config")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class AppFormConfigEntity extends BaseEntity {

    // APP ID
    @Column(name = "app_id")
    private String appId;

    // Login URL
    @Column(name = "login_url")
    private String loginUrl;

    // Login name attribute
    @Column(name = "username_field")
    private String usernameField;

    // Login password attribute
    @Column(name = "password_field")
    private String passwordField;

    // Login password encryption type
    @Column(name = "password_encrypt_type")
    private FormEncryptType passwordEncryptType;

    // Login password encryption key
    @Column(name = "password_encrypt_key")
    private String passwordEncryptKey;

    // Username encryption type
    @Column(name = "username_encrypt_type")
    private FormEncryptType usernameEncryptType;

    // Username encryption key
    @Column(name = "username_encrypt_key")
    private String usernameEncryptKey;

    // Login submission method
    @Column(name = "submit_type")
    private FormSubmitType submitType;

    // Other login information
    @Column(name = "other_field")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<OtherField> otherField;

    @Data
    @Schema(description = "表单其他信息")
    public static class OtherField implements Serializable {

        private String fieldName;

        private String fieldValue;
    }
}
