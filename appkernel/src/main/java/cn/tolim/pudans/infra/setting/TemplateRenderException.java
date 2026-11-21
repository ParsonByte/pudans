package cn.tolim.pudans.infra.setting;

public class TemplateRenderException extends RuntimeException {

    // 表示模板解析或渲染失败。
    public TemplateRenderException(String message) {
        super(message);
    }
}
