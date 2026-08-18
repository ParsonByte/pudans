package cn.tolim.pudans.contract.infra;

import java.net.URI;
import reactor.core.publisher.Mono;


public interface ExternalLinkProcessor {


    String processLink(String link);


    Mono<URI> processLink(URI uri);
}
