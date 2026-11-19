package cn.tolim.pudans.core.ai.rag;

public record Citation(
        int index,
        long docId,
        String fileName,
        Integer pageNo,
        String headingPath,
        double score,
        String snippet) {

    public Citation {
        if (index < 1) {
            throw new IllegalArgumentException("引用编号必须大于 0。");
        }
        if (docId <= 0) {
            throw new IllegalArgumentException("文档标识必须为正数。");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空。");
        }
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("引用分数必须是有限数值。");
        }
        if (snippet == null) {
            throw new IllegalArgumentException("引用片段不能为空。");
        }
    }
}
