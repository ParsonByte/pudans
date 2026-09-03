package cn.tolim.pudans.infra.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.UnaryOperator;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.util.InvalidUrlException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder.ParserType;
import org.springframework.web.util.UriUtils;


@Slf4j
@UtilityClass
public class PudansUtils {


    public static boolean isXhr(HttpHeaders headers) {
        return headers.getOrEmpty("X-Requested-With").contains("XMLHttpRequest");
    }


    public static String readClassPathResourceAsString(String location) {
        ClassPathResource classPathResource = new ClassPathResource(location);
        try (InputStream inputStream = classPathResource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    String.format("Failed to read class path file as string from location [%s]", location), e);
        }
    }


    public static String userAgentFrom(ServerRequest request) {
        HttpHeaders httpHeaders = request.headers().asHttpHeaders();

        String userAgent = httpHeaders.getFirst(HttpHeaders.USER_AGENT);
        if (StringUtils.isBlank(userAgent)) {

            userAgent = httpHeaders.getFirst("Sec-CH-UA");
        }
        return StringUtils.defaultIfBlank(userAgent, "unknown");
    }

    public static String getDayText(Instant instant) {
        Assert.notNull(instant, "Instant must not be null");
        int dayValue = instant.atZone(ZoneId.systemDefault()).getDayOfMonth();
        return StringUtils.leftPad(String.valueOf(dayValue), 2, '0');
    }

    public static String getMonthText(Instant instant) {
        Assert.notNull(instant, "Instant must not be null");
        int monthValue = instant.atZone(ZoneId.systemDefault()).getMonthValue();
        return StringUtils.leftPad(String.valueOf(monthValue), 2, '0');
    }

    public static String getYearText(Instant instant) {
        Assert.notNull(instant, "Instant must not be null");
        return String.valueOf(instant.atZone(ZoneId.systemDefault()).getYear());
    }


    public static UnaryOperator<ServerRequest> noCache() {
        return request -> {
            request.exchange().getAttributes().put("cn.tolim.pudans.no-cache", true);
            return request;
        };
    }


    public static URI safeToUri(String uri) {

        var decodedUri = UriUtils.decode(uri, StandardCharsets.UTF_8);
        UriComponentsBuilder uriBuilder;
        try {
            uriBuilder = UriComponentsBuilder.fromUriString(decodedUri);
        } catch (InvalidUrlException e) {
            uriBuilder = UriComponentsBuilder.fromUriString(decodedUri, ParserType.WHAT_WG);
        }
        return uriBuilder.build(false).encode().toUri();
    }


    public static URI safeToUriFromDecoded(String uri) {
        var normalizedUri = normalizeHttpHost(uri);
        try {
            return URI.create(normalizedUri);
        } catch (IllegalArgumentException e) {
            return safeToUri(normalizedUri);
        }
    }

    private static String normalizeHttpHost(String uri) {
        if (!StringUtils.startsWithIgnoreCase(uri, "http://") && !StringUtils.startsWithIgnoreCase(uri, "https://")) {

            return uri;
        }
        var normalizedUri = ExternalUrlUtils.toAscii(uri);
        return normalizedUri != null ? normalizedUri : uri;
    }
}
