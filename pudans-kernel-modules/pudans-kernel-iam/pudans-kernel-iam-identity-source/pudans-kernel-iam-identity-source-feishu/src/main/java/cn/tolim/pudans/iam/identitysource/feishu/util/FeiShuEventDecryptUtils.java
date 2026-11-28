package cn.tolim.pudans.iam.identitysource.feishu.util;

import cn.tolim.pudans.iam.infra.security.crypto.spissatus.SpissatusKeyedSupport;
import cn.tolim.spissatus4j.contract.algorithm.AlgorithmId;
import cn.tolim.spissatus4j.contract.api.Spissatus;
import cn.tolim.spissatus4j.contract.crypto.DigestRequest;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

// Decrypt data
@SuppressWarnings("AlibabaUndefineMagicConstant")
public class FeiShuEventDecryptUtils {

    private static final int IV_BYTES = 16;
    private static final int MAX_PADDING_BYTES = 16;

    private static volatile Spissatus facade;

    // Derives the Feishu event key through the spissatus4j digest facade
    private static byte[] init(String key) {
        return facade().crypto().digest(new DigestRequest(AlgorithmId.SHA_256,
                key.getBytes(StandardCharsets.UTF_8), SpissatusKeyedSupport.caller()));
    }

    // Feishu mandates AES-CBC without padding and an IV prefix, so the protocol
    // ciphertext has no wire-compatible spissatus4j AEAD equivalent
    public static String decrypt(String key, String base64) throws Exception {
        byte[] decode = Base64.getDecoder().decode(base64);
        Cipher cipher = Cipher.getInstance("AES/CBC/NOPADDING");
        byte[] iv = new byte[IV_BYTES];
        System.arraycopy(decode, 0, iv, 0, IV_BYTES);
        byte[] data = new byte[decode.length - IV_BYTES];
        System.arraycopy(decode, IV_BYTES, data, 0, data.length);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(init(key), "AES"),
                new IvParameterSpec(iv));
        byte[] r = cipher.doFinal(data);
        if (r.length > 0) {
            int p = r.length - 1;
            for (; p >= 0 && r[p] <= MAX_PADDING_BYTES; p--) {
            }
            if (p != r.length - 1) {
                byte[] rr = new byte[p + 1];
                System.arraycopy(r, 0, rr, 0, p + 1);
                r = rr;
            }
        }
        return new String(r, StandardCharsets.UTF_8);
    }

    private static Spissatus facade() {
        Spissatus current = facade;
        if (current == null) {
            synchronized (FeiShuEventDecryptUtils.class) {
                current = facade;
                if (current == null) {
                    current = SpissatusKeyedSupport.createFacade();
                    facade = current;
                }
            }
        }
        return current;
    }
}