package cn.tolim.pudans.iam.communal.entity.account;

import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.SoftDelete;

import java.io.Serial;
import java.time.LocalDateTime;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Accessors(chain = true)
@Entity
@Table(name = "pudans_user_history_password")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class UserHistoryPasswordEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = -2619231849746900857L;
    // Username
    @Column(name = "user_id")
    private String userId;

    // Password
    @Column(name = "password_")
    @JsonIgnore
    private String password;

    // Change time
    @Column(name = "change_time")
    private LocalDateTime changeTime;
}
