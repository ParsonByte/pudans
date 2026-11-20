package cn.tolim.pudans.core.ai.harness.agent;

import cn.tolim.pudans.core.ai.harness.compaction.CompactionConfig;
import cn.tolim.pudans.core.ai.harness.journal.ThreadJournalStore;
import cn.tolim.pudans.core.ai.harness.observability.HarnessObservability;
import cn.tolim.pudans.core.ai.harness.permission.PermissionEngine;
import cn.tolim.pudans.core.ai.harness.skill.SkillRuntime;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseRetrievalTool;
import cn.tolim.pudans.core.ai.harness.subagent.SubagentTools;
import cn.tolim.pudans.core.ai.harness.team.TeamTools;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkit;
import cn.tolim.pudans.core.ai.harness.tool.ToolResultEvictionConfig;
import cn.tolim.pudans.core.ai.harness.tool.ToolResultStore;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import java.util.List;
import reactor.core.publisher.Mono;

public final class HarnessAgent implements Agent {

    private final ReActAgent delegate;

    private HarnessAgent(ReActAgent delegate) {
        this.delegate = delegate;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public Mono<AgentResult> call(String input, RuntimeContext context) {
        return delegate.call(input, context);
    }

    @Override
    public AgentRun start(String input, RuntimeContext context) {
        return delegate.start(input, context);
    }

    public AgentRun start(UserMessage input, RuntimeContext context, boolean streaming) {
        return delegate.start(input, context, streaming);
    }

    public <T> Mono<T> call(String input, Class<T> type, RuntimeContext context) {
        return delegate.call(input, type, context);
    }

    public static final class Builder {

        private final ReActAgent.Builder delegate = ReActAgent.builder();

        public Builder name(String name) {
            delegate.name(name);
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            delegate.systemPrompt(systemPrompt);
            return this;
        }

        public Builder chatModel(ChatModel chatModel) {
            delegate.chatModel(chatModel);
            return this;
        }

        public Builder streamingChatModel(StreamingChatModel streamingChatModel) {
            delegate.streamingChatModel(streamingChatModel);
            return this;
        }

        public Builder toolkit(HarnessToolkit toolkit) {
            delegate.toolkit(toolkit);
            return this;
        }

        public Builder stateStore(AgentStateStore stateStore) {
            delegate.stateStore(stateStore);
            return this;
        }

        public Builder journalStore(ThreadJournalStore journalStore) {
            delegate.journalStore(journalStore);
            return this;
        }

        public Builder maxIters(int maxIters) {
            delegate.maxIters(maxIters);
            return this;
        }

        public Builder temperature(Double temperature) {
            delegate.temperature(temperature);
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            delegate.maxOutputTokens(maxOutputTokens);
            return this;
        }

        public Builder topP(Double topP) {
            delegate.topP(topP);
            return this;
        }

        public Builder topK(Integer topK) {
            delegate.topK(topK);
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            delegate.stopSequences(stopSequences);
            return this;
        }

        public Builder permissionEngine(PermissionEngine permissionEngine) {
            delegate.permissionEngine(permissionEngine);
            return this;
        }

        public Builder planModeEnabled(boolean planModeEnabled) {
            delegate.planModeEnabled(planModeEnabled);
            return this;
        }

        public Builder todoEnabled(boolean todoEnabled) {
            delegate.todoEnabled(todoEnabled);
            return this;
        }

        public Builder compaction(CompactionConfig compaction) {
            delegate.compaction(compaction);
            return this;
        }

        public Builder toolResultEviction(ToolResultEvictionConfig evictionConfig) {
            delegate.toolResultEviction(evictionConfig);
            return this;
        }

        public Builder toolResultStore(ToolResultStore toolResultStore) {
            delegate.toolResultStore(toolResultStore);
            return this;
        }

        public Builder skillRuntime(SkillRuntime skillRuntime) {
            delegate.skillRuntime(skillRuntime);
            return this;
        }

        public Builder subagentTools(SubagentTools subagentTools) {
            delegate.subagentTools(subagentTools);
            return this;
        }

        public Builder teamTools(TeamTools teamTools) {
            delegate.teamTools(teamTools);
            return this;
        }

        public Builder knowledgeBaseRetrievalTool(KnowledgeBaseRetrievalTool tool) {
            delegate.knowledgeBaseRetrievalTool(tool);
            return this;
        }

        public Builder observability(HarnessObservability observability) {
            delegate.observability(observability);
            return this;
        }

        public HarnessAgent build() {
            return new HarnessAgent(delegate.build());
        }
    }
}