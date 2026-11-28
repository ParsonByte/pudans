package cn.tolim.pudans.iam.communal.entity.setting;

import cn.tolim.pudans.iam.communal.enums.MailType;
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

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Entity
@Accessors(chain = true)
@Table(name = "pudans_mail_template")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class MailTemplateEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 5983857137670090984L;
    // Template type
    @Column(name = "type_")
    private MailType type;

    // Sender address. Supported macros: ${client_name}, ${time}, ${user_email}, ${client_description}, and ${verify_code}. Example: ${client_name} <support@yourcompany.com>
    @Column(name = "sender_")
    private String sender;

    // Subject
    // You can include these macros: ${client_name}, ${time}, ${client_description}, ${user_email}. Example: You are changing your binding email; your verification code is ${verify_code}.
    @Column(name = "subject_")
    private String subject;

    // Content
    @Column(name = "content_")
    private String content;
}
