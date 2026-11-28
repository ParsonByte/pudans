package cn.tolim.pudans.iam.communal.entity.app;

import cn.tolim.pudans.iam.communal.enums.app.AppCertUsingType;
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

import java.math.BigInteger;
import java.time.LocalDateTime;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Entity
@Accessors(chain = true)
@Table(name = "pudans_app_cert")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class AppCertEntity extends BaseEntity {

    public static final String APP_ID_FIELD_NAME = "appId";

    public static final String USING_TYPE_FIELD_NAME = "usingType";

    // Application ID
    @Column(name = "app_id")
    private String appId;

    // Certificate serial number
    @Column(name = "serial_")
    private BigInteger serial;

    // Subject information
    @Column(name = "subject_")
    private String subject;

    // Issuer information
    @Column(name = "issuer_")
    private String issuer;

    // Start time
    @Column(name = "begin_date")
    private LocalDateTime beginDate;

    // End time
    @Column(name = "end_date")
    private LocalDateTime endDate;

    // Validity days
    @Column(name = "validity_")
    private Integer validity;

    // Algorithm
    @Column(name = "sign_algo")
    private String signAlgo;

    // Private key length
    @Column(name = "key_long")
    private Integer keyLong;

    // Private key
    @Column(name = "private_key")
    private String privateKey;

    // Public key
    @Column(name = "public_key")
    private String publicKey;

    // Certificate
    @Column(name = "cert_")
    private String cert;

    // Usage type
    @Column(name = "using_type")
    private AppCertUsingType usingType;
}
