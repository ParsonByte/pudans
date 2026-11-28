package cn.tolim.pudans.iam.authentication.core.template;

import cn.tolim.pudans.iam.infra.exception.TemplateNotExistException;
import cn.tolim.pudans.iam.infra.result.ApiRestResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


@Slf4j
public class BindIdentityProviderTemplate {

    // FreeMarker configuration instantiation
    private final Configuration freemarkerTemplateConfiguration = new Configuration(
            Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);

    public BindIdentityProviderTemplate() {
        configFreemarkerTemplate();
    }

    // Return binding template
    public void sendAuthorizationResponse(HttpServletResponse response,
                                          ApiRestResult<Void> result) throws IOException {
        try {
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(ContentType.TEXT_HTML.getMimeType());
            Template template = freemarkerTemplateConfiguration.getTemplate("bind_redirect.ftlh");
            Map<String, Object> data = new HashMap<>(16);
            data.put("nonce", System.currentTimeMillis());
            ObjectMapper objectMapper = new ObjectMapper();
            data.put("result", objectMapper.writeValueAsString(result));
            template.process(data, response.getWriter());
        } catch (Exception e) {
            log.error("返回绑定提供商Template异常", e);
            // Html
            response.sendError(HttpStatus.BAD_REQUEST.value(), e.getMessage());
        }
    }

    private void configFreemarkerTemplate() {
        try {
            // Template storage path
            freemarkerTemplateConfiguration
                    .setTemplateLoader(new ClassTemplateLoader(this.getClass(), "/template/"));
            // Code
            freemarkerTemplateConfiguration.setDefaultEncoding(StandardCharsets.UTF_8.name());
        } catch (Exception exception) {
            throw new TemplateNotExistException(exception);
        }
    }
}
