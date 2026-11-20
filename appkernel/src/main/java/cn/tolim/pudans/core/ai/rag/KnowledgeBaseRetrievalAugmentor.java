package cn.tolim.pudans.core.ai.rag;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public final class KnowledgeBaseRetrievalAugmentor implements RetrievalAugmentor {

    private final ContentRetriever retriever;

    public KnowledgeBaseRetrievalAugmentor(ContentRetriever retriever) {
        this.retriever = Objects.requireNonNull(retriever, "内容检索器不能为空");
    }

    @Override
    public AugmentationResult augment(AugmentationRequest request) {
        if (!(request.chatMessage() instanceof UserMessage userMessage)) {
            return new AugmentationResult(request.chatMessage(), List.of());
        }
        var query = text(userMessage);
        if (!StringUtils.hasText(query)) {
            return new AugmentationResult(userMessage, List.of());
        }
        var contents = retriever.retrieve(Query.from(query, request.metadata()));
        if (contents.isEmpty()) {
            return new AugmentationResult(userMessage, List.of());
        }
        var knowledge = contents.stream()
                .map(content -> content.textSegment().text())
                .collect(Collectors.joining("\n\n"));
        var augmented = new ArrayList<Content>();
        var injected = false;
        for (var content : userMessage.contents()) {
            if (!injected && content instanceof TextContent text) {
                augmented.add(TextContent.from(text.text() + "\n\n" + knowledge));
                injected = true;
            } else {
                augmented.add(content);
            }
        }
        if (!injected) {
            augmented.add(TextContent.from(knowledge));
        }
        return new AugmentationResult(userMessage.toBuilder().contents(augmented).build(), contents);
    }

    @Override
    public CompletableFuture<AugmentationResult> augmentAsync(AugmentationRequest request) {
        return CompletableFuture.supplyAsync(() -> augment(request), Thread::startVirtualThread);
    }

    private static String text(UserMessage message) {
        return message.contents().stream()
                .filter(TextContent.class::isInstance)
                .map(TextContent.class::cast)
                .map(TextContent::text)
                .findFirst()
                .orElse("");
    }
}