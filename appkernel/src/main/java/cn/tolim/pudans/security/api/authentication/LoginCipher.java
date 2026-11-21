package cn.tolim.pudans.security.api.authentication;

import com.nimbusds.jose.jwk.JWK;
import reactor.core.publisher.Mono;

public interface LoginCipher {


    Mono<byte[]> decrypt(byte[] encryptedMessage);


    Mono<byte[]> readPublicKey();


    String getKeyId();


    JWK getJwk();
}
