package cn.tolim.pudans.iam.identitysource.feishu.domain.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;

// dept
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetDepartmentResponse implements Serializable {
    // Department name
    // Field permissions (any one is sufficient):
    // Read contacts as the application
    // Get department basic information
    private String name;
    // Parent department ID
    // Create a root department; use "0" for this parameter
    // Field permissions (any one is sufficient):
    // Read contacts as the application
    // Get department hierarchy information
    @JsonProperty("parent_department_id")
    private String parentDepartmentId;
    // Custom department ID
    // Field permissions (any one is sufficient):
    // Read contacts as the application
    // Get department basic information
    @JsonProperty("department_id")
    private String departmentId;
    // Open ID of the department
    @JsonProperty("open_department_id")
    private String openDepartmentId;
    // Department leader user ID
    // Field permissions (any one is sufficient):
    // Read contacts as the application
    // Get department hierarchy information
    @JsonProperty("leader_user_id")
    private String leaderUserId;
    // Department group ID
    // Field permissions (any one is sufficient):
    // Read contacts as the application
    // Get department basic information
    @JsonProperty("chat_id")
    private String chatId;
    // Department sort order among peers
    // Field permissions (any one is sufficient):
    // Read contacts as the application
    // Get department hierarchy information
    private String order;
    // Number of users in the department
    // Field permissions (any one is sufficient):
    // Read contacts as the application
    // Get department hierarchy information
    @JsonProperty("member_count")
    private int memberCount;
    // Department status
    // Field permissions (any one is sufficient):
    // Read contacts as the application
    // Get department basic information
    private Status status;
    // Whether to create a department group; disabled by default
    @JsonProperty("create_group_chat")
    private boolean createGroupChat;

    // status
    @Data
    public static class Status implements Serializable {
        // Deleted
        @JsonProperty(IS_DELETED_COLUMN)
        private boolean isDeleted;
    }
}
