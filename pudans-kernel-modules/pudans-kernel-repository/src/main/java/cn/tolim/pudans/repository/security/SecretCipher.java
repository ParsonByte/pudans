package cn.tolim.pudans.repository.security;

public interface SecretCipher {

    // Encrypt a plaintext secret and return its encoded form
    String encrypt(String plaintext);

    // Decrypt a value when it carries the encrypted prefix, otherwise return it unchanged
    String decryptIfEncrypted(String value);
}