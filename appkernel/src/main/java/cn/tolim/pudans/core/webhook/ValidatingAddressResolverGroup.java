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
public final class ValidatingAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {

    private final OutboundRequestValidator validator;

    private final AddressResolverGroup<InetSocketAddress> delegate;

    // 使用默认解析器创建带地址校验的解析组。
    public ValidatingAddressResolverGroup(OutboundRequestValidator validator) {
        this(validator, DefaultAddressResolverGroup.INSTANCE);
    }

    // 保存自定义解析器，供连接阶段复用。
    public ValidatingAddressResolverGroup(
            OutboundRequestValidator validator, AddressResolverGroup<InetSocketAddress> delegate) {
        this.validator = validator;
        this.delegate = delegate;
    }

    // 为每个事件循环创建带校验的地址解析器。
    @Override
    protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) throws Exception {
        return new ValidatingAddressResolver(delegate.getResolver(executor), validator, executor);
    }

    private static final class ValidatingAddressResolver implements AddressResolver<InetSocketAddress> {

        private final AddressResolver<InetSocketAddress> delegate;

        private final OutboundRequestValidator validator;

        private final EventExecutor executor;

        // 包装真实解析器并保留原解析能力。
        private ValidatingAddressResolver(
                AddressResolver<InetSocketAddress> delegate,
                OutboundRequestValidator validator,
                EventExecutor executor) {
            this.delegate = delegate;
            this.validator = validator;
            this.executor = executor;
        }

        // 透传底层解析器的支持判断。
        @Override
        public boolean isSupported(SocketAddress address) {
            return delegate.isSupported(address);
        }

        // 透传底层解析器的完成状态。
        @Override
        public boolean isResolved(SocketAddress address) {
            return delegate.isResolved(address);
        }

        // 解析单个地址并复用当前事件循环。
        @Override
        public Future<InetSocketAddress> resolve(SocketAddress address) {
            return resolve(address, executor.newPromise());
        }

        // 解析后校验地址，拒绝不可路由结果。
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

        // 解析全部地址并复用当前事件循环。
        @Override
        public Future<List<InetSocketAddress>> resolveAll(SocketAddress address) {
            return resolveAll(address, executor.newPromise());
        }

        // 校验全部解析结果，任一不可路由即失败。
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

        // 关闭底层解析器释放资源。
        @Override
        public void close() {
            delegate.close();
        }

        // 构造统一的不可路由地址异常。
        private static IllegalArgumentException nonRoutable() {
            return new IllegalArgumentException(
                    "出站目标解析到不可路由的地址。");
        }
    }
}
