export type AiChatComposerAddAction = "cloud-computer" | "create-material" | "library" | "upload";

export type AiChatComposerMode = "expert" | "fast";

export type AiChatComposerPanel = "add" | "skills" | null;

export type AiChatComposerVariant = "chat" | "work";

export type AiChatComposerSkill = {
    description?: string;
    id: string;
    label: string;
};

export type AiChatComposerTool = "chat" | "image" | "podcast" | "project" | "transcription" | "video" | "writing";

export const AI_CHAT_TOOL_PLACEHOLDERS: Record<AiChatComposerTool, string> = {
    chat: "发消息或按住空格说话...",
    image: "描述你想生成的图片",
    podcast: "输入播客主题或上传文稿",
    project: "输入项目需求，整理任务和资料",
    transcription: "选择录音，或输入需要转写的内容",
    video: "描述你想生成的视频",
    writing: "告诉我想写什么",
};
