package cn.tolim.pudans.iam.infra.repository.base;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity extends IdEntity {
    public static final String LAST_MODIFIED_BY = "updateBy";
    public static final String IS_DELETED_COLUMN = "is_deleted";
    public static final String CREATE_TIME = "createTime";
    public static final String CREATE_BY = "createBy";
    public static final String LAST_MODIFIED_TIME = "updateTime";

    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;

    @CreatedBy
    @Access(AccessType.PROPERTY)
    @Column(name = "create_by")
    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    @CreatedDate
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Access(AccessType.PROPERTY)
    @Column(name = "create_time")
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @LastModifiedBy
    @Access(AccessType.PROPERTY)
    @Column(name = "update_by")
    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    @LastModifiedDate
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Access(AccessType.PROPERTY)
    @Column(name = "update_time")
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Access(AccessType.PROPERTY)
    @Column(name = "remark_")
    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    @Override
    public String toString() {
        return "BaseEntity(createBy=" + createBy + ", createTime=" + createTime + ", updateBy="
                + updateBy + ", updateTime=" + updateTime + ", remark=" + remark + ")";
    }
}
