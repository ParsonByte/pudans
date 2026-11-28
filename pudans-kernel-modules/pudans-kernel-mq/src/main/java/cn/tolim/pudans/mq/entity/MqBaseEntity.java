package cn.tolim.pudans.mq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass
public abstract class MqBaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = -6202957192071483511L;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id_", nullable = false, length = 64)
    private String id;

    @Column(name = "create_by", length = 100)
    private String createBy;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_by", length = 100)
    private String updateBy;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "remark_", length = 500)
    private String remark;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        if (updateTime == null) {
            updateTime = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}