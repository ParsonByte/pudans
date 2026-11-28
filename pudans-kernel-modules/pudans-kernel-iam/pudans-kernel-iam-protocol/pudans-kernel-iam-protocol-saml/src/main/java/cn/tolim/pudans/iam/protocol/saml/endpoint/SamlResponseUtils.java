package cn.tolim.pudans.iam.protocol.saml.endpoint;

import cn.tolim.pudans.iam.protocol.saml.util.SamlXmlUtils;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


public final class SamlResponseUtils {

    private SamlResponseUtils() {
    }

    // Write an auto-submitting HTML form that posts the SAML response to the consumer URL.
    public static void writePostBinding(HttpServletResponse response, String consumerUrl,
                                        String signedResponseXml, String relayState)
            throws IOException {
        String encodedResponse = Base64.getEncoder().encodeToString(
                signedResponseXml.getBytes(StandardCharsets.UTF_8));
        String html = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
                + "<title>Redirecting</title></head>"
                + "<body onload=\"document.forms[0].submit()\">"
                + "<form method=\"post\" action=\"" + SamlXmlUtils.escapeHtml(consumerUrl) + "\">"
                + "<input type=\"hidden\" name=\"SAMLResponse\" value=\""
                + SamlXmlUtils.escapeHtml(encodedResponse) + "\"/>"
                + "<input type=\"hidden\" name=\"RelayState\" value=\""
                + SamlXmlUtils.escapeHtml(relayState == null ? "" : relayState) + "\"/>"
                + "<noscript><button type=\"submit\">Continue</button></noscript>"
                + "</form></body></html>";
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(html);
    }

    // Write a SAML error response.
    public static void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message == null ? "SAML protocol error" : message);
    }
}