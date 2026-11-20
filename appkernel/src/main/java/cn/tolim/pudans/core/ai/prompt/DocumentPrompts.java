package cn.tolim.pudans.core.ai.prompt;

public final class DocumentPrompts {

    public static final String MARKDOWN =
            "你是专业文档撰写助手。请根据用户要求输出结构完整、标题清晰的 Markdown 文档，"
                    + "只输出文档正文，不要输出解释、前言或代码围栏。";

    public static final String HTML =
            "你是专业文档撰写助手。请输出完整、可独立打开、带内联样式的 HTML5 文档，"
                    + "只输出 HTML 源码，不要输出解释或代码围栏。";

    private DocumentPrompts() {}
}