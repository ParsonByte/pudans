package cn.tolim.pudans.core.ai.rag;

public record RetrievalCandidate(
        long chunkId,
        long documentId,
        String kbName,
        String fileName,
        String content,
        Integer pageNo,
        String headingPath,
        double score) {

    public RetrievalCandidate {
        if (chunkId <= 0 || documentId <= 0) {
            throw new IllegalArgumentException("切片和文档标识必须为正数。");
        }
        if (kbName == null || kbName.isBlank()) {
            throw new IllegalArgumentException("知识库编码不能为空。");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空。");
        }
        if (content == null) {
            throw new IllegalArgumentException("切片内容不能为空。");
        }
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("检索分数必须是有限数值。");
        }
    }

    public RetrievalCandidate withContent(String newContent) {
        return new RetrievalCandidate(chunkId, documentId, kbName, fileName, newContent, pageNo, headingPath, score);
    }
}
