package cn.tolim.pudans.iam.communal.entity.app.query;

import cn.tolim.pudans.iam.communal.enums.app.AppPolicySubjectType;
import lombok.Data;


@Data
public class AppAccessPolicyQueryParam {

    // Application ID
    private String appId;

    // Authorization principal
    private String subjectName;

    // Authorization principal ID
    private String subjectId;

    // Principal type (user, group, organization)
    private AppPolicySubjectType subjectType;

    // Application name
    private String appName;
}
