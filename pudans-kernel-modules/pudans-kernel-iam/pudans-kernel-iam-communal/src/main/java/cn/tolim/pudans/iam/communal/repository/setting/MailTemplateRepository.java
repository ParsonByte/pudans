package cn.tolim.pudans.iam.communal.repository.setting;

import cn.tolim.pudans.iam.communal.entity.setting.MailTemplateEntity;
import cn.tolim.pudans.iam.communal.enums.MailType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;


@Repository
public interface MailTemplateRepository extends JpaRepository<MailTemplateEntity, Long> {
    // Query templates by type
    // Parameter type: MailType
    // Returns: MailTemplateEntity
    MailTemplateEntity findByType(@Param("type") MailType type);

    // Delete modules by module type
    // Parameter type: MailType
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteByType(@Param("type") MailType type);
}
