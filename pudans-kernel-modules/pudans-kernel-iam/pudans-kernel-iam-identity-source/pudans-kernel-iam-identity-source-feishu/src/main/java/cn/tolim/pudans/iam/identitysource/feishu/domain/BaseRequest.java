package cn.tolim.pudans.iam.identitysource.feishu.domain;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;


@Data
public class BaseRequest implements Serializable {
    // User ID type
    // Example value: "open_id"
    // Available values:
    // open_id: the user's open ID
    // union_id: the user's union ID
    // user_id: the user's user ID
    // Default value: open_id
    // When the value is user_id, field permission requirements apply:
    // Get user ID for self-built applications only
    @JSONField(name = "user_id_type")
    private String userIdType = "user_id";
    // Department ID type used in this call
    // Example value: "open_department_id"
    // Available values:
    // department_id: identifies a department with a custom department_id
    // open_department_id: identifies a department with open_department_id
    // Default value: open_department_id
    @JSONField(name = "departmentId_type")
    private String departmentIdType = "department_id";
}
