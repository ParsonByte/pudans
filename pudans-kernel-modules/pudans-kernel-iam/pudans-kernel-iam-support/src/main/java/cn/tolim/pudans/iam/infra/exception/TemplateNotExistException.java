package cn.tolim.pudans.iam.infra.exception;

public class TemplateNotExistException extends PudansException {
    public TemplateNotExistException(Throwable cause) {
        super("模板不存在", cause);
    }
}
