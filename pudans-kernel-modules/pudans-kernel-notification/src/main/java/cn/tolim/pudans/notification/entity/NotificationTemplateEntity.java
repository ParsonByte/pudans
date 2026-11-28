package cn.tolim.pudans.notification.entity;

import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;

import java.io.Serial;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;

@Getter
@Setter
@Entity
@Table(name = "pudans_notification_template",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_template_code",
                columnNames = "template_code"))
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class NotificationTemplateEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 5175441204738693012L;

    // Stable template code referenced by send requests.
    @Column(name = "template_code", nullable = false, length = 100)
    private String code;

    @Column(name = "template_name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_", nullable = false, length = 32)
    private NotificationChannel channel;

    @Column(name = "subject_", columnDefinition = "TEXT")
    private String subject;

    @Column(name = "content_", columnDefinition = "TEXT")
    private String content;

    // Provider-side template identifier such as an Aliyun or Tencent SMS template.
    @Column(name = "provider_template_code", length = 100)
    private String providerTemplateCode;

    @Column(name = "language_", length = 20)
    private String language;

    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled;
}