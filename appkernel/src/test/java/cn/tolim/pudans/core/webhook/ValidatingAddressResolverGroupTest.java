package cn.tolim.pudans.core.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidatingAddressResolverGroupTest {

    private static final InetSocketAddress PUBLIC =
            new InetSocketAddress(address("93.184.216.34"), 443);

    private static final InetSocketAddress PRIVATE =
            new InetSocketAddress(address("127.0.0.1"), 443);

    private static InetAddress address(String value) {
        try {
            return InetAddress.getByName(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static AddressResolverGroup<InetSocketAddress> groupReturning(InetSocketAddress... results) {
        var queue = new ArrayList<>(List.of(results));
        AddressResolver<InetSocketAddress> resolver = new AddressResolver<>() {
            private int index;

            @Override
            public boolean isSupported(SocketAddress address) {
                return true;
            }

            @Override
            public boolean isResolved(SocketAddress address) {
                return false;
            }

            @Override
            public Future<InetSocketAddress> resolve(SocketAddress address) {
                return resolve(address, ImmediateEventExecutor.INSTANCE.newPromise());
            }

            @Override
            public Future<InetSocketAddress> resolve(
                    SocketAddress address, Promise<InetSocketAddress> promise) {
                var result = queue.get(Math.min(index++, queue.size() - 1));
                promise.setSuccess(result);
                return promise;
            }

            @Override
            public Future<List<InetSocketAddress>> resolveAll(SocketAddress address) {
                return resolveAll(address, ImmediateEventExecutor.INSTANCE.newPromise());
            }

            @Override
            public Future<List<InetSocketAddress>> resolveAll(
                    SocketAddress address, Promise<List<InetSocketAddress>> promise) {
                var result = queue.get(Math.min(index++, queue.size() - 1));
                promise.setSuccess(List.of(result));
                return promise;
            }

            @Override
            public void close() {}
        };
        return new AddressResolverGroup<>() {
            @Override
            protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
                return resolver;
            }
        };
    }

    private static Future<InetSocketAddress> resolve(
            ValidatingAddressResolverGroup group, InetSocketAddress target) {
        return group.getResolver(ImmediateEventExecutor.INSTANCE)
                .resolve(new InetSocketAddress("example.com", target.getPort()))
                .awaitUninterruptibly();
    }

    @Test
    void publicAddressIsAllowed() {
        var group = new ValidatingAddressResolverGroup(
                new OutboundRequestValidator(), groupReturning(PUBLIC));

        assertThat(resolve(group, PUBLIC).isSuccess()).isTrue();
    }

    @Test
    void privateAddressIsRejected() {
        var group = new ValidatingAddressResolverGroup(
                new OutboundRequestValidator(), groupReturning(PRIVATE));

        var result = resolve(group, PRIVATE);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rebindingIsRejectedOnTheSecondResolution() {
        var group = new ValidatingAddressResolverGroup(
                new OutboundRequestValidator(), groupReturning(PUBLIC, PRIVATE));
        var resolver = group.getResolver(ImmediateEventExecutor.INSTANCE);
        var target = new InetSocketAddress("example.com", 443);

        assertThat(resolver.resolve(target).awaitUninterruptibly().isSuccess()).isTrue();
        var rebound = resolver.resolve(target).awaitUninterruptibly();

        assertThat(rebound.isSuccess()).isFalse();
        assertThat(rebound.cause()).isInstanceOf(IllegalArgumentException.class);
    }
}
