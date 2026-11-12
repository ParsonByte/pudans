package cn.tolim.pudans.security.authentication;

import cn.tolim.pudans.infra.properties.SecurityProperties;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Mono;

/** Authenticated landing redirect. Anonymous users never reach this handler. */
@Controller
@RequiredArgsConstructor
class LandingController {

    private final SecurityProperties securityProperties;

    @GetMapping("/")
    Mono<ResponseEntity<Void>> landing() {
        return Mono.just(ResponseEntity.status(302)
                .location(URI.create(securityProperties.getPostLoginUrl()))
                .build());
    }
}
