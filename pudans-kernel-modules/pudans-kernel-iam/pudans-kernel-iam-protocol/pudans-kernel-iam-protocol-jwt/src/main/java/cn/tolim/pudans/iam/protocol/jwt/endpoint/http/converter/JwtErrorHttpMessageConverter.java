
package cn.tolim.pudans.iam.protocol.jwt.endpoint.http.converter;

import cn.tolim.pudans.iam.protocol.core.http.converter.HttpMessageConverters;
import cn.tolim.pudans.iam.protocol.jwt.endpoint.JwtParameterNames;
import cn.tolim.pudans.iam.protocol.jwt.exception.JwtError;
import org.apache.commons.collections4.MapUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;


public class JwtErrorHttpMessageConverter extends AbstractHttpMessageConverter<JwtError> {

    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private static final ParameterizedTypeReference<Map<String, Object>> STRING_OBJECT_MAP = new ParameterizedTypeReference<>() {
    };

    private final GenericHttpMessageConverter<Object> jsonMessageConverter = HttpMessageConverters
            .getJsonMessageConverter();

    protected Converter<Map<String, String>, JwtError> errorConverter = new JwtErrorConverter();

    protected Converter<JwtError, Map<String, String>> errorParametersConverter = new JwtErrorParametersConverter();

    public JwtErrorHttpMessageConverter() {
        super(DEFAULT_CHARSET, MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
    }

    @Override
    protected boolean supports(@NotNull Class<?> clazz) {
        return JwtError.class.isAssignableFrom(clazz);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected JwtError readInternal(@NotNull Class<? extends JwtError> clazz,
                                    @NotNull HttpInputMessage inputMessage) throws HttpMessageNotReadableException {
        try {
            Map<String, Object> errorParameters = (Map<String, Object>) this.jsonMessageConverter
                    .read(STRING_OBJECT_MAP.getType(), null, inputMessage);
            return this.errorConverter.convert(errorParameters.entrySet().stream().collect(
                    Collectors.toMap(Map.Entry::getKey, (entry) -> String.valueOf(entry.getValue()))));
        } catch (Exception ex) {
            throw new HttpMessageNotReadableException(
                    "读取 JWT 错误信息失败：" + ex.getMessage(), ex, inputMessage);
        }
    }

    @Override
    protected void writeInternal(@NotNull JwtError jwtError,
                                 @NotNull HttpOutputMessage outputMessage) throws HttpMessageNotWritableException {
        try {
            Map<String, String> errorParameters = this.errorParametersConverter.convert(jwtError);
            if (MapUtils.isNotEmpty(errorParameters)) {
                this.jsonMessageConverter.write(errorParameters, STRING_OBJECT_MAP.getType(),
                        MediaType.APPLICATION_JSON, outputMessage);
            }
        } catch (Exception ex) {
            throw new HttpMessageNotWritableException(
                    "写入 JWT 错误信息失败：" + ex.getMessage(), ex);
        }
    }


    public final void setErrorConverter(Converter<Map<String, String>, JwtError> errorConverter) {
        Assert.notNull(errorConverter, "errorConverter 不能为空");
        this.errorConverter = errorConverter;
    }


    public final void setErrorParametersConverter(Converter<JwtError, Map<String, String>> errorParametersConverter) {
        Assert.notNull(errorParametersConverter, "errorParametersConverter 不能为空");
        this.errorParametersConverter = errorParametersConverter;
    }


    private static class JwtErrorConverter implements Converter<Map<String, String>, JwtError> {

        @Override
        public JwtError convert(Map<String, String> parameters) {
            String errorCode = parameters.get(JwtParameterNames.ERROR);
            String errorDescription = parameters.get(JwtParameterNames.ERROR_DESCRIPTION);
            String errorUri = parameters.get(JwtParameterNames.ERROR_URI);
            return new JwtError(errorCode, errorDescription, errorUri);
        }

    }


    private static class JwtErrorParametersConverter implements
            Converter<JwtError, Map<String, String>> {

        @Override
        public Map<String, String> convert(@NonNull JwtError jwtError) {
            Map<String, String> parameters = new HashMap<>();
            parameters.put(JwtParameterNames.ERROR, jwtError.getErrorCode());
            if (StringUtils.hasText(jwtError.getDescription())) {
                parameters.put(JwtParameterNames.ERROR_DESCRIPTION, jwtError.getDescription());
            }
            if (StringUtils.hasText(jwtError.getUri())) {
                parameters.put(JwtParameterNames.ERROR_URI, jwtError.getUri());
            }
            return parameters;
        }
    }

}
