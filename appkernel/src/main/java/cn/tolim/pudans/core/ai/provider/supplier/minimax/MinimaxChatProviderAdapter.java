package cn.tolim.pudans.core.ai.provider.supplier.minimax;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import cn.tolim.pudans.core.ai.provider.OpenAiChatProviderAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class MinimaxChatProviderAdapter extends OpenAiChatProviderAdapter {

    private final ObjectMapper mapper;

    public MinimaxChatProviderAdapter(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            AiProviderAccountService accountService) {
        super(webClient, mapper, accountService);
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
    }

    @Override
    public AiProvider.Protocol protocol() {
        return AiProvider.Protocol.MINIMAX_CHAT;
    }

    @Override
    protected String serialize(AiChatIr request, AiModel model, boolean stream) {
        return super.serialize(transformFiles(request), model, stream);
    }

    @Override
    protected void validateResponse(JsonNode root) {
        MinimaxProtocol.rejectBaseResp(root);
    }

    @Override
    protected void validateStreamEvent(JsonNode root) {
        MinimaxProtocol.rejectBaseResp(root);
    }

    private AiChatIr transformFiles(AiChatIr request) {
        var messages = new ArrayList<AiChatMessage>();
        for (var message : request.messages()) {
            var parts = new ArrayList<AiContentPart>();
            for (var part : message.parts()) {
                if (part instanceof AiContentPart.File file && fileUrl(file) != null) {
                    var type = fileType(file.mediaType());
                    if (type != null) {
                        parts.add(new AiContentPart.Custom(type, block(type, fileUrl(file))));
                        continue;
                    }
                }
                parts.add(part);
            }
            messages.add(new AiChatMessage(message.role(), parts));
        }
        return new AiChatIr(messages, request.tools(), request.requestParams(), request.stream());
    }

    private JsonNode block(String type, String url) {
        var block = mapper.createObjectNode();
        block.put("type", type);
        block.putObject(type).put("url", url);
        return block;
    }

    private static String fileType(String mediaType) {
        if (mediaType == null) {
            return null;
        }
        if (mediaType.regionMatches(true, 0, "image/", 0, 6)) {
            return "image_url";
        }
        if (mediaType.regionMatches(true, 0, "video/", 0, 6)) {
            return "video_url";
        }
        return null;
    }

    private static String fileUrl(AiContentPart.File file) {
        if (StringUtils.hasText(file.fileId())) {
            return "mm_file://" + file.fileId();
        }
        if (StringUtils.hasText(file.url())) {
            return file.url();
        }
        if (StringUtils.hasText(file.data())) {
            var mediaType = StringUtils.hasText(file.mediaType())
                    ? file.mediaType()
                    : "application/octet-stream";
            return "data:" + mediaType + ";base64," + file.data();
        }
        return null;
    }
}
