package cn.tolim.pudans.iam.communal.entity.account;

import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.SoftDelete;

import java.io.Serial;
import java.time.LocalDateTime;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Accessors(chain = true)
@Getter
@Setter
@ToString
@Entity
@Table(name = "pudans_user_idp_bind")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class UserIdpBindEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = -14364708756807242L;

    // User ID
    @Column(name = "user_id")
    private String userId;

    // Third-party user table ID
    @ManyToOne
    @JoinColumn(name = "third_party_user_id")
    @ToString.Exclude
    private ThirdPartyUserEntity thirdPartyUser;

    // Binding time
    @Column(name = "bind_time")
    private LocalDateTime bindTime;
}
