package cn.tolim.pudans.iam.communal.entity.account;

import cn.tolim.pudans.iam.communal.enums.account.UserIdType;
import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.Hibernate;
import org.hibernate.annotations.SoftDelete;

import java.io.Serial;
import java.util.Objects;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Accessors(chain = true)
@Entity
@Table(name = "pudans_user_detail")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class UserDetailEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = -3599183663669763315L;
    // User table ID
    @Column(name = "user_id")
    private String userId;

    // Document type
    @Column(name = "id_type")
    private UserIdType idType;

    // ID card number
    @Column(name = "id_card")
    private String idCard;

    // Personal homepage
    @Column(name = "website_")
    private String website;

    // Address
    @Column(name = "address_")
    private String address;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        UserDetailEntity that = (UserDetailEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
