package cn.tolim.pudans.iam.synchronizer.endpoint;

import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceEntity;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceRepository;
import cn.tolim.pudans.iam.identitysource.core.IdentitySource;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import cn.tolim.pudans.iam.infra.trace.Trace;
import cn.tolim.pudans.iam.synchronizer.configuration.IdentitySourceBeanUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static cn.tolim.pudans.iam.communal.constant.SynchronizerConstants.EVENT_RECEIVE_PATH;


@Slf4j
@Tag(name = "IAM 身份源事件接收")
@RestController
@RequestMapping(EVENT_RECEIVE_PATH)
@SuppressWarnings("unchecked")
public class IdentitySourceEventReceiveEndpoint {

    private final ApplicationContext applicationContext;
    private final IdentitySourceRepository identitySourceRepository;

    public IdentitySourceEventReceiveEndpoint(ApplicationContext applicationContext,
                                              IdentitySourceRepository identitySourceRepository) {
        this.applicationContext = applicationContext;
        this.identitySourceRepository = identitySourceRepository;
    }

    // Handle event notification
    @Operation(summary = "接收身份源事件")
    @Trace
    @RequestMapping(value = "/{code}")
    public ResponseEntity<?> receive(HttpServletRequest request, @PathVariable String code,
                                     @RequestBody(required = false) String body) {
        Optional<IdentitySourceEntity> optional = identitySourceRepository.findByCode(code);
        if (optional.isPresent()) {
            IdentitySourceEntity entity = optional.get();
            // Enabled and configured
            if (entity.getEnabled() && entity.getConfigured()) {
                String beanName = IdentitySourceBeanUtils.getSourceBeanName(optional.get().getId());
                IdentitySource<IdentitySourceConfig> identitySource = (IdentitySource<IdentitySourceConfig>) applicationContext
                        .getBean(beanName);
                Object event = identitySource.event(request, body);
                return ResponseEntity.ok(event);
            }
        }
        return ResponseEntity.ok().build();
    }
}
