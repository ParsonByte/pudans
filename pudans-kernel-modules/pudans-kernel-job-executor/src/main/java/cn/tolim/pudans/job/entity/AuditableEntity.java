package cn.tolim.pudans.job.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

// Common identity and bookkeeping columns for job entities
@MappedSuperclass
public abstract class AuditableEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id_", nullable = false, length = 64)
    private String id;

    @Column(name = "create_by", length = 64)
    private String createBy;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_by", length = 64)
    private String updateBy;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "remark_")
    private String remark;

    // Fill timestamps when rows are inserted outside of a Spring auditing context
    @PrePersist
    protected void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        updateTime = now;
    }

    // Refresh the update timestamp on every modification
    @PreUpdate
    protected void preUpdate() {
        updateTime = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}