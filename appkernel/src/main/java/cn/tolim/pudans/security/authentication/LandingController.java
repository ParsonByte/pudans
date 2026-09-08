package cn.tolim.pudans.security.authentication;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Mono;

/** Authenticated landing redirect. Anonymous users never reach this handler. */
@Controller
class LandingController {

    @GetMapping("/")
    Mono<ResponseEntity<Void>> landing() {
        return Mono.just(ResponseEntity.status(302)
                .location(URI.create("/uc/"))
                .build());
    }
}
