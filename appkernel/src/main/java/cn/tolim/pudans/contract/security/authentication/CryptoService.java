package cn.tolim.pudans.contract.security.authentication;

import com.nimbusds.jose.jwk.JWK;
import reactor.core.publisher.Mono;

public interface CryptoService {


    Mono<byte[]> decrypt(byte[] encryptedMessage);


    Mono<byte[]> readPublicKey();


    String getKeyId();


    JWK getJwk();
}
