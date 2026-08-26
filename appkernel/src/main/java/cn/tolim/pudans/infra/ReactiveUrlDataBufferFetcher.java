package cn.tolim.pudans.infra;

import java.net.URI;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface ReactiveUrlDataBufferFetcher {


    Flux<DataBuffer> fetch(URI uri);


    Mono<HttpHeaders> head(URI uri);


    Mono<ResponseEntity<Flux<DataBuffer>>> fetchResponseEntity(URI uri);
}
