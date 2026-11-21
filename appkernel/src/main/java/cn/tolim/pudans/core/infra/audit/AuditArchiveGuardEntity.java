package cn.tolim.pudans.core.infra.audit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("audit_archive_guard")
public class AuditArchiveGuardEntity {

    @Id
    private Integer id;

    private Boolean enabled;
}
