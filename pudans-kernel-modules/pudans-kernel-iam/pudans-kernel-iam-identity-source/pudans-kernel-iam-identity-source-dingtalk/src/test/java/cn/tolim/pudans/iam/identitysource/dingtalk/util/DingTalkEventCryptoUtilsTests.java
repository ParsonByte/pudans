package cn.tolim.pudans.iam.identitysource.dingtalk.util;

import cn.tolim.pudans.iam.identitysource.dingtalk.util.DingTalkEventCryptoUtils.DingTalkEncryptException;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class DingTalkEventCryptoUtilsTests {

    private static final String TOKEN = "pudans-token";
    private static final String CORP_ID = "ding-corp";
    private static final String AES_KEY = encodingAesKey();

    @Test
    void encryptsAndDecryptsEventMessages() throws Exception {
        DingTalkEventCryptoUtils utils = new DingTalkEventCryptoUtils(TOKEN, AES_KEY, CORP_ID);
        String plaintext = "{\"EventType\":\"check_url\",\"CorpId\":\"ding-corp\"}";

        Map<String, String> encrypted = utils.getEncryptedMap(plaintext);

        assertThat(encrypted).containsKeys("msg_signature", "encrypt", "timeStamp", "nonce");
        assertThat(encrypted.get("encrypt")).doesNotContain("check_url");
        assertThat(utils.getDecryptMsg(encrypted.get("msg_signature"),
                encrypted.get("timeStamp"), encrypted.get("nonce"), encrypted.get("encrypt")))
                .isEqualTo(plaintext);
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        DingTalkEventCryptoUtils utils = new DingTalkEventCryptoUtils(TOKEN, AES_KEY, CORP_ID);
        Map<String, String> encrypted = utils.getEncryptedMap("{\"EventType\":\"check_url\"}");
        String signature = encrypted.get("msg_signature");
        String tampered = (signature.startsWith("0") ? "1" : "0") + signature.substring(1);

        Throwable thrown = catchThrowable(() -> utils.getDecryptMsg(tampered,
                encrypted.get("timeStamp"), encrypted.get("nonce"), encrypted.get("encrypt")));

        assertThat(thrown).isInstanceOf(DingTalkEncryptException.class);
        assertThat(((DingTalkEncryptException) thrown).getCode())
                .isEqualTo(DingTalkEncryptException.COMPUTE_SIGNATURE_ERROR);
    }

    @Test
    void rejectsMismatchedCorpId() throws Exception {
        DingTalkEventCryptoUtils utils = new DingTalkEventCryptoUtils(TOKEN, AES_KEY, CORP_ID);
        Map<String, String> encrypted = utils.getEncryptedMap("{\"EventType\":\"check_url\"}");
        DingTalkEventCryptoUtils other = new DingTalkEventCryptoUtils(TOKEN, AES_KEY, "other-corp");

        Throwable thrown = catchThrowable(() -> other.getDecryptMsg(encrypted.get("msg_signature"),
                encrypted.get("timeStamp"), encrypted.get("nonce"), encrypted.get("encrypt")));

        assertThat(thrown).isInstanceOf(DingTalkEncryptException.class);
        assertThat(((DingTalkEncryptException) thrown).getCode())
                .isEqualTo(DingTalkEncryptException.COMPUTE_DECRYPT_TEXT_CORPID_ERROR);
    }

    @Test
    void rejectsTamperedCiphertext() throws Exception {
        DingTalkEventCryptoUtils utils = new DingTalkEventCryptoUtils(TOKEN, AES_KEY, CORP_ID);
        Map<String, String> encrypted = utils.getEncryptedMap("{\"EventType\":\"check_url\"}");
        String ciphertext = encrypted.get("encrypt");
        char[] chars = ciphertext.toCharArray();
        int index = chars.length / 2;
        chars[index] = chars[index] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);
        String signature = utils.getSignature(TOKEN, encrypted.get("timeStamp"),
                encrypted.get("nonce"), tampered);

        Throwable thrown = catchThrowable(() -> utils.getDecryptMsg(signature,
                encrypted.get("timeStamp"), encrypted.get("nonce"), tampered));

        assertThat(thrown).isInstanceOf(DingTalkEncryptException.class);
        assertThat(((DingTalkEncryptException) thrown).getCode()).isIn(
                DingTalkEncryptException.COMPUTE_DECRYPT_TEXT_ERROR,
                DingTalkEncryptException.COMPUTE_DECRYPT_TEXT_LENGTH_ERROR,
                DingTalkEncryptException.COMPUTE_DECRYPT_TEXT_CORPID_ERROR);
    }

    @Test
    void rejectsInvalidEncodingAesKey() {
        assertThatThrownBy(() -> new DingTalkEventCryptoUtils(TOKEN, "too-short", CORP_ID))
                .isInstanceOf(DingTalkEncryptException.class)
                .satisfies(thrown -> assertThat(
                        ((DingTalkEncryptException) thrown).getCode())
                        .isEqualTo(DingTalkEncryptException.AES_KEY_ILLEGAL));
    }

    private static String encodingAesKey() {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) (index * 7 + 1);
        }
        return Base64.getEncoder().encodeToString(key).substring(0, 43);
    }
}