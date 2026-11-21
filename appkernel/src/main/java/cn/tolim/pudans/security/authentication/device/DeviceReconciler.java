package cn.tolim.pudans.security.authentication.device;

import static cn.tolim.pudans.extension.api.ExtensionUtil.addFinalizers;
import static cn.tolim.pudans.extension.api.ExtensionUtil.isDeleted;
import static cn.tolim.pudans.extension.api.ExtensionUtil.removeFinalizers;
import static cn.tolim.pudans.extension.api.index.query.Queries.equal;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.security.api.Device;
import cn.tolim.pudans.extension.api.InternalReactiveExtensionClient;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.controller.Controller;
import cn.tolim.pudans.extension.api.controller.ControllerBuilder;
import cn.tolim.pudans.extension.api.controller.ReactiveReconciler;
import cn.tolim.pudans.extension.api.controller.Reconciler;
import cn.tolim.pudans.extension.api.router.selector.FieldSelector;

@Component
@RequiredArgsConstructor
public class DeviceReconciler implements ReactiveReconciler<Reconciler.Request> {

    private static final int MAX_DEVICES = 10;

    static final String FINALIZER_NAME = "device-protection";

    private final ReactiveSessionRepository<?> sessionRepository;

    private final InternalReactiveExtensionClient client;

    @Override
    public Mono<Result> reconcileReactive(Request request) {
        return client.fetch(Device.class, request.name())
                .flatMap(device -> {
                    if (isDeleted(device)) {
                        if (!removeFinalizers(device.getMetadata(), Set.of(FINALIZER_NAME))) {
                            return Mono.just(Result.doNotRetry());
                        }
                        return sessionRepository
                                .deleteById(device.getSpec().getSessionId())
                                .then(client.updateInternal(device))
                                .thenReturn(Result.doNotRetry());
                    }
                    var update = addFinalizers(device.getMetadata(), Set.of(FINALIZER_NAME))
                            ? client.updateInternal(device)
                            : Mono.just(device);
                    return update.then(revokeInactiveDevices(device.getSpec().getPrincipalName()))
                            .thenReturn(Result.doNotRetry());
                })
                .switchIfEmpty(Mono.just(Result.doNotRetry()));
    }

    private Mono<Void> revokeInactiveDevices(String principalName) {
        var listOptions = new ListOptions();
        listOptions.setFieldSelector(FieldSelector.of(equal("spec.principalName", principalName)));
        return client
                .listAll(Device.class, listOptions, Sort.by("metadata.creationTimestamp").descending())
                .skip(MAX_DEVICES)
                .concatMap(device -> sessionRepository
                        .findById(device.getSpec().getSessionId())
                        .hasElement()
                        .flatMap(exists -> exists ? Mono.empty() : client.delete(device)))
                .then();
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new Device()).syncAllOnStart(false).build();
    }
}
