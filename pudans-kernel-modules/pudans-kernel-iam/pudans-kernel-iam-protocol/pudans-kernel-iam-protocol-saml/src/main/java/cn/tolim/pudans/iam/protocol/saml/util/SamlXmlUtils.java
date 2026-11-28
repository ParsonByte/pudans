package cn.tolim.pudans.iam.protocol.saml.util;

import cn.tolim.pudans.iam.protocol.saml.exception.SamlErrorCodes;
import cn.tolim.pudans.iam.protocol.saml.exception.SamlProtocolException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.Inflater;


public final class SamlXmlUtils {

    private SamlXmlUtils() {
    }

    // Escape a value for XML text and attribute content.
    public static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> builder.append("&amp;");
                case '<' -> builder.append("&lt;");
                case '>' -> builder.append("&gt;");
                case '"' -> builder.append("&quot;");
                case '\'' -> builder.append("&apos;");
                default -> builder.append(c);
            }
        }
        return builder.toString();
    }

    // Escape a value for HTML attribute content.
    public static String escapeHtml(String value) {
        return escapeXml(value);
    }

    // Parse an XML document with external entities and DTDs disabled.
    public static Document parseDocument(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new SamlProtocolException(SamlErrorCodes.XML_ERROR, "SAML XML is empty");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception exception) {
            throw new SamlProtocolException(SamlErrorCodes.XML_ERROR,
                    "Unable to parse SAML XML: " + exception.getMessage(), exception);
        }
    }

    // Find the first child element with the given namespace and local name.
    public static Element firstChild(Element parent, String namespace, String localName) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            if (localName.equals(element.getLocalName())
                    && (namespace == null || namespace.equals(element.getNamespaceURI()))) {
                return element;
            }
        }
        return null;
    }

    // Return all direct child elements with the given namespace and local name.
    public static List<Element> children(Element parent, String namespace, String localName) {
        List<Element> result = new ArrayList<>();
        if (parent == null) {
            return result;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            if (localName.equals(element.getLocalName())
                    && (namespace == null || namespace.equals(element.getNamespaceURI()))) {
                result.add(element);
            }
        }
        return result;
    }

    // Return the trimmed text content of an element.
    public static String text(Element element) {
        return element == null ? null : element.getTextContent().trim();
    }

    // Serialize a DOM document to an XML string without an XML declaration.
    public static String serializeDocument(Document document) {
        if (document == null) {
            throw new SamlProtocolException(SamlErrorCodes.XML_ERROR,
                    "SAML XML document is empty");
        }
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception exception) {
            throw new SamlProtocolException(SamlErrorCodes.XML_ERROR,
                    "Unable to serialize SAML XML: " + exception.getMessage(), exception);
        }
    }

    // Decode a redirect-binding SAML message.
    public static String decodeRedirectBinding(String value) {
        if (value == null || value.isBlank()) {
            throw new SamlProtocolException(SamlErrorCodes.INVALID_REQUEST,
                    "SAMLRequest is empty");
        }
        byte[] compressed;
        try {
            compressed = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new SamlProtocolException(SamlErrorCodes.INVALID_REQUEST,
                    "SAMLRequest is not valid base64", exception);
        }
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(compressed);
            ByteArrayOutputStream output = new ByteArrayOutputStream(compressed.length * 4);
            byte[] buffer = new byte[4096];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        break;
                    }
                    continue;
                }
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new SamlProtocolException(SamlErrorCodes.INVALID_REQUEST,
                    "Unable to inflate SAMLRequest: " + exception.getMessage(), exception);
        } finally {
            inflater.end();
        }
    }

    // Encode a SAML message with the redirect binding.
    public static String encodeRedirectBinding(String xml) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            deflater.setInput(xml.getBytes(StandardCharsets.UTF_8));
            deflater.finish();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                output.write(buffer, 0, count);
            }
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } finally {
            deflater.end();
        }
    }

    // Decode a post-binding SAML message.
    public static String decodePostBinding(String value) {
        if (value == null || value.isBlank()) {
            throw new SamlProtocolException(SamlErrorCodes.INVALID_REQUEST,
                    "SAML message is empty");
        }
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new SamlProtocolException(SamlErrorCodes.INVALID_REQUEST,
                    "SAML message is not valid base64", exception);
        }
    }

    // Strip PEM headers and whitespace, returning the raw base64 certificate body.
    public static String normalizeCertificate(String certificate) {
        if (certificate == null) {
            return null;
        }
        return certificate
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s+", "");
    }

    // Strip any PEM header and whitespace, returning the raw base64 body.
    public static String normalizePem(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s+", "");
    }

    // Generate a random SAML identifier.
    public static String newId() {
        String value = java.util.UUID.randomUUID().toString().replace("-", "");
        return "_" + value;
    }

    // Generate a random short identifier without the leading underscore.
    public static String newRawId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
}