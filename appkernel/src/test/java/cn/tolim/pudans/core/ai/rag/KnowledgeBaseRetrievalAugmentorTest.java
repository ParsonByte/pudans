package cn.tolim.pudans.core.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseRetrievalAugmentorTest {

    @Mock
    ContentRetriever retriever;

    @Test
    void injectsKnowledgeIntoTheFirstTextContentAndKeepsAttachments() {
        var image = ImageContent.from(Image.builder()
                .base64Data("aW1hZ2U=")
                .mimeType("image/png")
                .build());
        var message = UserMessage.from(List.of(TextContent.from("怎么安装？"), image));
        when(retriever.retrieve(any()))
                .thenReturn(List.of(dev.langchain4j.rag.content.Content.from(
                        TextSegment.from("【知识库参考】先备份。"))));
        var augmentor = new KnowledgeBaseRetrievalAugmentor(retriever);

        var result = augmentor.augment(request(message));

        assertThat(result.contents()).hasSize(1);
        var contents = ((UserMessage) result.chatMessage()).contents();
        assertThat(contents).hasSize(2);
        assertThat(((TextContent) contents.get(0)).text())
                .contains("怎么安装？")
                .contains("【知识库参考】先备份。");
        assertThat(contents.get(1)).isInstanceOf(ImageContent.class);
    }

    @Test
    void returnsTheOriginalMessageWhenNothingIsRetrieved() {
        var message = UserMessage.from("你好");
        when(retriever.retrieve(any())).thenReturn(List.of());
        var augmentor = new KnowledgeBaseRetrievalAugmentor(retriever);

        var result = augmentor.augment(request(message));

        assertThat(result.chatMessage()).isSameAs(message);
        assertThat(result.contents()).isEmpty();
    }

    @Test
    void augmentsAsynchronously() throws Exception {
        var message = UserMessage.from("怎么安装？");
        when(retriever.retrieve(any()))
                .thenReturn(List.of(dev.langchain4j.rag.content.Content.from(TextSegment.from("先备份。"))));
        var augmentor = new KnowledgeBaseRetrievalAugmentor(retriever);

        var result = augmentor.augmentAsync(request(message)).get();

        assertThat(result.chatMessage()).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) result.chatMessage()).singleText()).contains("先备份。");
    }

    private static AugmentationRequest request(UserMessage message) {
        return new AugmentationRequest(message, new Metadata(message, null, List.of()));
    }
}