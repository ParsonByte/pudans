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
import java.util.Objects;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Accessors(chain = true)
@Getter
@Setter
@ToString
@Entity
@Table(name = "pudans_third_party_user")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class ThirdPartyUserEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 3716649301707341983L;
    // unionId
    @Column(name = "union_id")
    public String unionId;
    // OpenId
    @Column(name = "open_id")
    private String openId;
    // Identity provider ID
    @Column(name = "idp_id")
    private String idpId;

    // Identity provider type
    @Column(name = "idp_type")
    private String idpType;

    // Personal email
    @Column(name = "email_")
    private String email;

    // Country code for the phone number
    @Column(name = "state_code")
    private String stateCode;

    // Phone number
    @Column(name = "mobile_")
    private String mobile;

    // Nickname
    @Column(name = "nick_name")
    private String nickName;

    // Avatar URL
    @Column(name = "avatar_url")
    private String avatarUrl;

    // Additional information
    @Column(name = "addition_info")
    private String additionInfo;

    public boolean equals(ThirdPartyUserEntity that) {
        if (this == that) {
            return true;
        }
        if (that == null || getClass() != that.getClass()) {
            return false;
        }
        return Objects.equals(openId, that.openId) && Objects.equals(unionId, that.unionId)
                && Objects.equals(idpId, that.idpId) && Objects.equals(idpType, that.idpType)
                && Objects.equals(email, that.email) && Objects.equals(stateCode, that.stateCode)
                && Objects.equals(mobile, that.mobile) && Objects.equals(nickName, that.nickName)
                && Objects.equals(avatarUrl, that.avatarUrl)
                && Objects.equals(additionInfo, that.additionInfo);
    }
}
