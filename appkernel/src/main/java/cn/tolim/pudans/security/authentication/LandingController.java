package cn.tolim.pudans.security.authentication;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Mono;

/** Minimal root response. */
@Controller
class LandingController {

    @GetMapping("/")
    Mono<ResponseEntity<Void>> landing() {
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
