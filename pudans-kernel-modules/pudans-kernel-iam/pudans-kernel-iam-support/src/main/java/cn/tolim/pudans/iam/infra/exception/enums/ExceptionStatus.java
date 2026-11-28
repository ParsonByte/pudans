package cn.tolim.pudans.iam.infra.exception.enums;

public enum ExceptionStatus {
    EX900000("900000", "未知异常"),
    EX900001("900001", "操作失败"),
    EX900002("900002", "请求处理失败"),
    EX900003("900003", "远程服务调用失败"),
    EX900004("900004", "远程服务超时"),
    EX900005("900005", "远程服务不可用"),
    EX900006("900006", "远程响应解析失败"),
    EX900007("900007", "数据访问失败"),
    EX900008("900008", "并发更新冲突"),
    EX900009("900009", "应用状态无效"),
    EX000100("000100", "无效请求"),
    EX000101("000101", "参数校验失败"),
    EX000102("000102", "验证码无效或已过期"),
    EX000103("000103", "用户未认证"),
    EX000104("000104", "访问被拒绝"),
    EX000105("000105", "账号已锁定"),
    EX000106("000106", "账号已禁用"),
    EX000107("000107", "用户不存在"),
    EX000108("000108", "资源不存在"),
    EX000109("000109", "资源已存在"),
    EX000201("000201", "身份提供商认证失败"),
    EX000202("000202", "身份提供商未配置"),
    EX000203("000203", "身份提供商返回了无效响应"),
    EX000205("000205", "身份源同步失败");

    private String code;
    private String message;

    ExceptionStatus(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
