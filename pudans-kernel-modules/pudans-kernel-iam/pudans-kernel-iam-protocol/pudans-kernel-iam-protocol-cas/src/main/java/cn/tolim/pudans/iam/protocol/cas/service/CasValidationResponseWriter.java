package cn.tolim.pudans.iam.protocol.cas.service;

import cn.tolim.pudans.iam.protocol.cas.model.CasServiceValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.CAS_NAMESPACE;


public final class CasValidationResponseWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CasValidationResponseWriter() {
    }

    // Write a CAS 2.0 or CAS 3.0 validation response.
    public static void writeValidationResponse(HttpServletResponse response,
                                               CasServiceValidationResult result,
                                               boolean includeAttributes, boolean json)
            throws IOException {
        if (json) {
            writeJson(response, result, includeAttributes);
            return;
        }
        writeXml(response, result, includeAttributes);
    }

    // Write the CAS 1.0 plain text response.
    public static void writeTextValidationResponse(HttpServletResponse response,
                                                   CasServiceValidationResult result)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/plain;charset=UTF-8");
        if (result.isSuccess()) {
            response.getWriter().write("yes\n" + result.getUsername() + "\n");
        } else {
            response.getWriter().write("no\n\n");
        }
    }

    // Write a plain text error response.
    public static void writeTextError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message == null ? "CAS protocol error" : message);
    }

    private static void writeXml(HttpServletResponse response, CasServiceValidationResult result,
                                 boolean includeAttributes) throws IOException {
        StringBuilder xml = new StringBuilder(512);
        xml.append("<cas:serviceResponse xmlns:cas=\"").append(CAS_NAMESPACE).append("\">");
        if (result.isSuccess()) {
            xml.append("<cas:authenticationSuccess><cas:user>")
                    .append(escapeXml(result.getUsername()))
                    .append("</cas:user>");
            if (includeAttributes && result.getAttributes() != null
                    && !result.getAttributes().isEmpty()) {
                xml.append("<cas:attributes>");
                result.getAttributes().forEach((name, value) -> {
                    String element = toXmlElementName(name);
                    xml.append("<cas:").append(element).append('>')
                            .append(escapeXml(value)).append("</cas:").append(element).append('>');
                });
                xml.append("</cas:attributes>");
            }
            xml.append("</cas:authenticationSuccess>");
        } else {
            xml.append("<cas:authenticationFailure code=\"")
                    .append(escapeXml(result.getFailureCode())).append("\">")
                    .append(escapeXml(result.getFailureMessage()))
                    .append("</cas:authenticationFailure>");
        }
        xml.append("</cas:serviceResponse>");
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/xml;charset=UTF-8");
        response.getWriter().write(xml.toString());
    }

    private static void writeJson(HttpServletResponse response, CasServiceValidationResult result,
                                  boolean includeAttributes) throws IOException {
        Map<String, Object> serviceResponse = new LinkedHashMap<>();
        if (result.isSuccess()) {
            Map<String, Object> success = new LinkedHashMap<>();
            success.put("user", result.getUsername());
            if (includeAttributes) {
                success.put("attributes", result.getAttributes() == null
                        ? Map.of() : result.getAttributes());
            }
            serviceResponse.put("authenticationSuccess", success);
        } else {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("code", result.getFailureCode());
            failure.put("description", result.getFailureMessage());
            serviceResponse.put("authenticationFailure", failure);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serviceResponse", serviceResponse);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
    }

    private static String toXmlElementName(String name) {
        if (name == null || name.isBlank()) {
            return "attribute";
        }
        StringBuilder builder = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean valid = Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.';
            builder.append(valid ? c : '_');
        }
        if (Character.isDigit(builder.charAt(0))) {
            builder.insert(0, '_');
        }
        return builder.toString();
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}