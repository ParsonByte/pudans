package cn.tolim.pudans.iam.communal.entity.app.po;

import cn.tolim.pudans.iam.communal.entity.app.AppAccessPolicyEntity;
import cn.tolim.pudans.iam.communal.enums.app.AppPolicySubjectType;
import cn.tolim.pudans.iam.communal.enums.app.AppProtocol;
import cn.tolim.pudans.iam.communal.enums.app.AppType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;


@EqualsAndHashCode(callSuper = true)
@Data
public class AppAccessPolicyPO extends AppAccessPolicyEntity {
    // Application name
    private String appName;

    // Application icon
    private String appIcon;

    // Template
    private String appTemplate;

    // Protocol
    private AppProtocol appProtocol;

    // Application type
    private AppType appType;

    // Authorization principal
    private String subjectName;

    public AppAccessPolicyPO(String id, String appId, String subjectId,
                             AppPolicySubjectType subjectType, Boolean enabled,
                             LocalDateTime createTime, String subjectName, String appName,
                             String appIcon, AppType appType, String appTemplate,
                             AppProtocol appProtocol) {

        super.setId(id);
        super.setAppId(appId);
        super.setSubjectId(subjectId);
        super.setSubjectType(subjectType);
        super.setSubjectType(subjectType);
        super.setEnabled(enabled);
        super.setCreateTime(createTime);
        this.subjectName = subjectName;
        this.appName = appName;
        this.appIcon = appIcon;
        this.appType = appType;
        this.appTemplate = appTemplate;
        this.appProtocol = appProtocol;
    }
}
