package cn.tolim.pudans.core.ai.harness.agent;

import cn.tolim.pudans.core.ai.harness.plan.PlanModeState;
import cn.tolim.pudans.core.ai.harness.plan.TodoItem;
import dev.langchain4j.data.message.ChatMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AgentState {

    private final List<ChatMessage> messages;

    private final Map<String, Object> attributes;

    private final PlanModeState planMode = new PlanModeState();

    private final List<TodoItem> todos = new ArrayList<>();

    private String summary;

    public AgentState() {
        this(new ArrayList<>(), "", new LinkedHashMap<>());
    }

    public AgentState(List<ChatMessage> messages, String summary, Map<String, Object> attributes) {
        this.messages = new ArrayList<>(messages == null ? List.of() : messages);
        this.summary = summary == null ? "" : summary;
        this.attributes = new LinkedHashMap<>(attributes == null ? Map.of() : attributes);
    }

    public List<ChatMessage> messages() {
        return List.copyOf(messages);
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
    }

    public void replaceMessages(List<ChatMessage> messages) {
        this.messages.clear();
        if (messages != null) {
            this.messages.addAll(messages);
        }
    }

    public String summary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary == null ? "" : summary;
    }

    public Map<String, Object> attributes() {
        return Map.copyOf(attributes);
    }

    public void setAttribute(String name, Object value) {
        if (value == null) {
            attributes.remove(name);
        } else {
            attributes.put(name, value);
        }
    }

    public PlanModeState planMode() {
        return planMode;
    }

    public List<TodoItem> todos() {
        return List.copyOf(todos);
    }

    public void setTodos(List<TodoItem> todos) {
        this.todos.clear();
        if (todos != null) {
            this.todos.addAll(todos);
        }
    }

    public AgentState copy() {
        var copy = new AgentState(messages, summary, attributes);
        var planCopy = planMode.copy();
        copy.planMode.restore(planCopy.active(), planCopy.planContent());
        copy.setTodos(todos);
        return copy;
    }
}