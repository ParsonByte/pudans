package cn.tolim.pudans.core.webhook;

import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

// 在连接时重新校验解析出的地址，防止预检后发生 DNS 重绑定。
final class ValidatingAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {

    private final OutboundRequestValidator validator;

    private final AddressResolverGroup<InetSocketAddress> delegate;

    ValidatingAddressResolverGroup(OutboundRequestValidator validator) {
        this(validator, DefaultAddressResolverGroup.INSTANCE);
    }

    ValidatingAddressResolverGroup(
            OutboundRequestValidator validator, AddressResolverGroup<InetSocketAddress> delegate) {
        this.validator = validator;
        this.delegate = delegate;
    }

    @Override
    protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) throws Exception {
        return new ValidatingAddressResolver(delegate.getResolver(executor), validator, executor);
    }

    private static final class ValidatingAddressResolver implements AddressResolver<InetSocketAddress> {

        private final AddressResolver<InetSocketAddress> delegate;

        private final OutboundRequestValidator validator;

        private final EventExecutor executor;

        private ValidatingAddressResolver(
                AddressResolver<InetSocketAddress> delegate,
                OutboundRequestValidator validator,
                EventExecutor executor) {
            this.delegate = delegate;
            this.validator = validator;
            this.executor = executor;
        }

        @Override
        public boolean isSupported(SocketAddress address) {
            return delegate.isSupported(address);
        }

        @Override
        public boolean isResolved(SocketAddress address) {
            return delegate.isResolved(address);
        }

        @Override
        public Future<InetSocketAddress> resolve(SocketAddress address) {
            return resolve(address, executor.newPromise());
        }

        @Override
        public Future<InetSocketAddress> resolve(
                SocketAddress address, Promise<InetSocketAddress> promise) {
            var delegatePromise = executor.<InetSocketAddress>newPromise();
            delegatePromise.addListener((Future<InetSocketAddress> future) -> {
                if (!future.isSuccess()) {
                    promise.setFailure(future.cause());
                    return;
                }
                var resolved = future.getNow();
                if (resolved == null
                        || resolved.isUnresolved()
                        || OutboundRequestValidator.isBlocked(resolved.getAddress())) {
                    promise.setFailure(nonRoutable());
                } else {
                    promise.setSuccess(resolved);
                }
            });
            delegate.resolve(address, delegatePromise);
            return promise;
        }

        @Override
        public Future<List<InetSocketAddress>> resolveAll(SocketAddress address) {
            return resolveAll(address, executor.newPromise());
        }

        @Override
        public Future<List<InetSocketAddress>> resolveAll(
                SocketAddress address, Promise<List<InetSocketAddress>> promise) {
            var delegatePromise = executor.<List<InetSocketAddress>>newPromise();
            delegatePromise.addListener((Future<List<InetSocketAddress>> future) -> {
                if (!future.isSuccess()) {
                    promise.setFailure(future.cause());
                    return;
                }
                var resolved = future.getNow();
                if (resolved == null || resolved.isEmpty()) {
                    promise.setFailure(nonRoutable());
                    return;
                }
                for (var inetSocketAddress : resolved) {
                    if (inetSocketAddress == null
                            || inetSocketAddress.isUnresolved()
                            || OutboundRequestValidator.isBlocked(inetSocketAddress.getAddress())) {
                        promise.setFailure(nonRoutable());
                        return;
                    }
                }
                promise.setSuccess(resolved);
            });
            delegate.resolveAll(address, delegatePromise);
            return promise;
        }

        @Override
        public void close() {
            delegate.close();
        }

        private static IllegalArgumentException nonRoutable() {
            return new IllegalArgumentException(
                    "Webhook 目标解析到不可路由的地址。");
        }
    }
}
