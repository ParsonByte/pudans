package cn.tolim.pudans.core.ai.rag;

import cn.tolim.pudans.contract.ai.resource.KnowledgeBase;

public final class KnowledgeBaseCodes {

    private KnowledgeBaseCodes() {}

    public static String codeOf(KnowledgeBase knowledgeBase) {
        if (knowledgeBase == null || knowledgeBase.getMetadata() == null) {
            throw new IllegalArgumentException("知识库元数据不能为空。");
        }
        var name = knowledgeBase.getMetadata().getName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("知识库名称不能为空。");
        }
        var slash = name.lastIndexOf('/');
        return slash < 0 ? name : name.substring(slash + 1);
    }
}
