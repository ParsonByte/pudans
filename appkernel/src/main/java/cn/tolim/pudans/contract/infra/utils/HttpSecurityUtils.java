package cn.tolim.pudans.contract.infra.utils;

import io.netty.channel.ChannelOption;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.netty.http.client.HttpClient;

@Slf4j
public enum HttpSecurityUtils {
    ;


    public static HttpClient secureHttpClient() {
        return HttpClient.create()
                .followRedirect(false)
                .responseTimeout(Duration.ofSeconds(10))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .resolvedAddressesSelector((config, resolvedAddresses) -> resolvedAddresses.stream()
                        .filter(address -> address instanceof InetSocketAddress)
                        .map(address -> (InetSocketAddress) address)
                        .filter(inetSocketAddress -> !isBlocked(inetSocketAddress.getAddress()))
                        .toList());
    }

    public static ExchangeFilterFunction maxResponseSizeFilter(long maxByteCount) {
        return (request, next) -> next.exchange(request)
                .map(response -> response.mutate()
                        .body(body -> {
                            var countDown = new AtomicLong(maxByteCount);
                            return body.handle((buffer, sink) -> {
                                long remainder = countDown.addAndGet(-buffer.readableByteCount());
                                if (remainder < 0) {
                                    DataBufferUtils.release(buffer);
                                    sink.error(new DataBufferLimitException(
                                            "响应体超过允许的最大大小 " + maxByteCount
                                                    + " 字节"));
                                } else {
                                    sink.next(buffer);
                                }
                            });
                        })
                        .build());
    }

    static boolean isBlocked(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isSpecialUseIpv4(address)
                || isUniqueLocalIpv6(address);
    }

    static boolean isSpecialUseIpv4(InetAddress address) {
        if (!(address instanceof Inet4Address inet4Address)) {
            return false;
        }
        byte[] bytes = inet4Address.getAddress();
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);


        return first == 0


                || (first == 100 && second >= 64 && second <= 127)

                || (first == 198 && (second == 18 || second == 19))


                || first >= 240;
    }

    static boolean isUniqueLocalIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address inet6Address)) {
            return false;
        }
        return (inet6Address.getAddress()[0] & 0xfe) == 0xfc;
    }
}
