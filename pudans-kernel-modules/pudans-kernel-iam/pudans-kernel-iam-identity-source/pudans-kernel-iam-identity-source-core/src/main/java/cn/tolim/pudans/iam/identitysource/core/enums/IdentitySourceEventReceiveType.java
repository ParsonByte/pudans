package cn.tolim.pudans.iam.identitysource.core.enums;

// Event notification type
public enum IdentitySourceEventReceiveType {
    // User added
    USER_ADD,
    // User modified
    USER_MODIFY,
    // User departed
    USER_LEAVE,

    // User deleted
    USER_REMOVE,

    // Department created
    DEPT_CREATE,

    // Department modified
    DEPT_MODIFY,
    // Department deleted
    DEPT_REMOVE
}
