package cn.tolim.pudans.communal.feign;

import cn.tolim.pudans.communal.core.error.ApiException;
import cn.tolim.pudans.communal.core.error.CommonErrorCode;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

// Converts Feign error responses carrying ApiResponse bodies into ApiException.
public class PudansErrorDecoder implements ErrorDecoder {

    private static final Logger LOGGER = LoggerFactory.getLogger(PudansErrorDecoder.class);
    private static final int MAX_BODY_CHARACTERS = 2048;
    private static final int MAX_MESSAGE_BODY_CHARACTERS = 512;

    private final ObjectMapper objectMapper;
    private final boolean includeResponseBody;

    public PudansErrorDecoder(ObjectMapper objectMapper, boolean includeResponseBody) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        this.objectMapper = objectMapper;
        this.includeResponseBody = includeResponseBody;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        String body = readBody(response);
        RemoteError remoteError = parseRemoteError(body);
        String code = remoteError.code() == null || remoteError.code().isBlank()
                ? defaultCode(status)
                : remoteError.code();
        String message = remoteError.message() == null || remoteError.message().isBlank()
                ? defaultMessage(status)
                : remoteError.message();
        String exceptionMessage = "Remote call failed: " + methodKey + " returned " + status + " (" + code + ")";
        if (!message.isBlank()) {
            exceptionMessage = exceptionMessage + ": " + message;
        }
        if (includeResponseBody && !body.isBlank()) {
            exceptionMessage = exceptionMessage + " body=" + abbreviate(body);
        }
        LOGGER.warn("Feign request failed: method={}, status={}, code={}, message={}",
                methodKey, status, code, message);
        return new ApiException(code, status, exceptionMessage);
    }

    private String readBody(Response response) {
        if (response.body() == null) {
            return "";
        }
        try (Reader reader = response.body().asReader(StandardCharsets.UTF_8)) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[256];
            int read;
            while ((read = reader.read(buffer)) != -1 && builder.length() < MAX_BODY_CHARACTERS) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        } catch (IOException exception) {
            LOGGER.debug("Could not read Feign error response body for status {}", response.status(), exception);
            return "";
        }
    }

    private RemoteError parseRemoteError(String body) {
        if (body == null || body.isBlank()) {
            return RemoteError.EMPTY;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.isObject()) {
                return RemoteError.EMPTY;
            }
            JsonNode codeNode = root.path("code");
            JsonNode messageNode = root.path("message");
            String code = codeNode.isString() ? codeNode.stringValue() : null;
            String message = messageNode.isString() ? messageNode.stringValue() : null;
            return new RemoteError(code, message);
        } catch (RuntimeException exception) {
            LOGGER.debug("Feign error response body is not an ApiResponse document", exception);
            return RemoteError.EMPTY;
        }
    }

    private String defaultCode(int status) {
        return switch (status) {
            case 400 -> CommonErrorCode.BAD_REQUEST.code();
            case 401 -> CommonErrorCode.UNAUTHORIZED.code();
            case 403 -> CommonErrorCode.FORBIDDEN.code();
            case 404 -> CommonErrorCode.NOT_FOUND.code();
            case 409 -> "CONFLICT";
            case 429 -> "TOO_MANY_REQUESTS";
            default -> status >= 500 ? CommonErrorCode.INTERNAL_ERROR.code() : "HTTP_" + status;
        };
    }

    private String defaultMessage(int status) {
        return switch (status) {
            case 400 -> CommonErrorCode.BAD_REQUEST.message();
            case 401 -> CommonErrorCode.UNAUTHORIZED.message();
            case 403 -> CommonErrorCode.FORBIDDEN.message();
            case 404 -> CommonErrorCode.NOT_FOUND.message();
            default -> "remote service returned HTTP " + status;
        };
    }

    private String abbreviate(String body) {
        if (body.length() <= MAX_MESSAGE_BODY_CHARACTERS) {
            return body;
        }
        return body.substring(0, MAX_MESSAGE_BODY_CHARACTERS) + "...";
    }

    private record RemoteError(String code, String message) {
        private static final RemoteError EMPTY = new RemoteError(null, null);
    }
}