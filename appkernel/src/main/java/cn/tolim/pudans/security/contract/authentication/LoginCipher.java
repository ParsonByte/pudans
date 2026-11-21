package cn.tolim.pudans.security.contract.authentication;

import com.nimbusds.jose.jwk.JWK;
import reactor.core.publisher.Mono;

public interface LoginCipher {


    // 使用当前私钥解密客户端提交的密文
    Mono<byte[]> decrypt(byte[] encryptedMessage);


    // 导出公钥供登录页加密密码
    Mono<byte[]> readPublicKey();


    // 密钥标识用于客户端选择公钥
    String getKeyId();


    // 以 JWK 形式提供公钥元数据
    JWK getJwk();
}
