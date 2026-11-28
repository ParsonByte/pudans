package cn.tolim.pudans.iam.appkernel.core.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;


public class AppTemplateNotExistException extends PudansException {

    public AppTemplateNotExistException() {
        super("app_template_not_exist", "应用模版不存在", BAD_REQUEST);
    }
}
