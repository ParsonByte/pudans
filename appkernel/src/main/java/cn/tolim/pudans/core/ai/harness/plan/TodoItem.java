package cn.tolim.pudans.core.ai.harness.plan;

public record TodoItem(String id, String content, String status) {

    public TodoItem {
        id = id == null ? "" : id;
        content = content == null ? "" : content;
        status = status == null ? "pending" : status;
    }
}