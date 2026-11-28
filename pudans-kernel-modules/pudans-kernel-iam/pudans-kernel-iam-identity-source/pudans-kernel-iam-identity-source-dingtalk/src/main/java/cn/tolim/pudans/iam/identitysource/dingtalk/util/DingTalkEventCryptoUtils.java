package cn.tolim.pudans.iam.identitysource.dingtalk.util;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

// DingTalk Open Platform encryption and decryption methods
@SuppressWarnings("ALL")
public class DingTalkEventCryptoUtils {

    private static final Charset CHARSET = Charset.forName("utf-8");
    private static final Base64 base64 = new Base64();
    // Ask getPaddingBytes fixed key length
    private static final Integer AES_ENCODE_KEY_LENGTH = 43;
    // Encrypted random string byte length
    private static final Integer RANDOM_LENGTH = 16;

    private byte[] aesKey;
    private String token;
    private String corpId;

    // Constructor
    // Parameter token Token configured by the developer in the DingTalk Open Platform
    // Parameter encodingAesKey EncodingAESKey configured in the DingTalk Open Platform
    // Parameter corpId AppKey for event subscription in a self-built application
    // Use corpId for a self-built application callback address
    // Use suiteKey for a third-party enterprise application
    // Throws DingTalkEncryptException Indicates failure; inspect the error code and message
    public DingTalkEventCryptoUtils(String token, String encodingAesKey,
                                    String corpId) throws DingTalkEncryptException {
        if (null == encodingAesKey || encodingAesKey.length() != AES_ENCODE_KEY_LENGTH) {
            throw new DingTalkEncryptException(DingTalkEncryptException.AES_KEY_ILLEGAL);
        }
        this.token = token;
        this.corpId = corpId;
        aesKey = Base64.decodeBase64(encodingAesKey + "=");
    }

    public Map<String, String> getEncryptedMap(String plaintext) throws DingTalkEncryptException {
        return getEncryptedMap(plaintext, System.currentTimeMillis(), Utils.getRandomStr(16));
    }

    // Encrypt the message body synchronized with the DingTalk Open Platform and return an encrypted map
    // Parameter plaintext Plaintext message body
    // Parameter timeStamp Timestamp
    // Parameter nonce Random string
    // Throws DingTalkEncryptException
    public Map<String, String> getEncryptedMap(String plaintext, Long timeStamp,
                                               String nonce) throws DingTalkEncryptException {
        if (null == plaintext) {
            throw new DingTalkEncryptException(
                    DingTalkEncryptException.ENCRYPTION_PLAINTEXT_ILLEGAL);
        }
        if (null == timeStamp) {
            throw new DingTalkEncryptException(
                    DingTalkEncryptException.ENCRYPTION_TIMESTAMP_ILLEGAL);
        }
        if (null == nonce) {
            throw new DingTalkEncryptException(DingTalkEncryptException.ENCRYPTION_NONCE_ILLEGAL);
        }
        // Encrypt
        String encrypt = encrypt(Utils.getRandomStr(RANDOM_LENGTH), plaintext);
        String signature = getSignature(token, String.valueOf(timeStamp), nonce, encrypt);
        Map<String, String> resultMap = new HashMap<String, String>();
        resultMap.put("msg_signature", signature);
        resultMap.put("encrypt", encrypt);
        resultMap.put("timeStamp", String.valueOf(timeStamp));
        resultMap.put("nonce", nonce);
        return resultMap;
    }

    // Decrypt ciphertext
    // Parameter msgSignature Signature string
    // Parameter timeStamp Timestamp
    // Parameter nonce Random string
    // Parameter encryptMsg Ciphertext
    // Returns Decrypted plaintext
    // Throws DingTalkEncryptException
    public String getDecryptMsg(String msgSignature, String timeStamp, String nonce,
                                String encryptMsg) throws DingTalkEncryptException {
        // Verify signature
        String signature = getSignature(token, timeStamp, nonce, encryptMsg);
        if (!signature.equals(msgSignature)) {
            throw new DingTalkEncryptException(DingTalkEncryptException.COMPUTE_SIGNATURE_ERROR);
        }
        // Decrypt
        String result = decrypt(encryptMsg);
        return result;
    }

    // Encrypt plaintext.
    // DingTalk mandates AES-CBC without padding plus custom padding, so this
    // protocol ciphertext has no wire-compatible spissatus4j AEAD equivalent
    // Parameter text Plaintext to encrypt
    // Returns Base64-encoded encrypted string
    private String encrypt(String random, String plaintext) throws DingTalkEncryptException {
        try {
            byte[] randomBytes = random.getBytes(CHARSET);
            byte[] plainTextBytes = plaintext.getBytes(CHARSET);
            byte[] lengthByte = Utils.int2Bytes(plainTextBytes.length);
            byte[] corpidBytes = corpId.getBytes(CHARSET);
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            byteStream.write(randomBytes);
            byteStream.write(lengthByte);
            byteStream.write(plainTextBytes);
            byteStream.write(corpidBytes);
            byte[] padBytes = PKCS7Padding.getPaddingBytes(byteStream.size());
            byteStream.write(padBytes);
            byte[] unencrypted = byteStream.toByteArray();
            byteStream.close();
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
            IvParameterSpec iv = new IvParameterSpec(aesKey, 0, 16);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv);
            byte[] encrypted = cipher.doFinal(unencrypted);
            String result = base64.encodeToString(encrypted);
            return result;
        } catch (Exception e) {
            throw new DingTalkEncryptException(DingTalkEncryptException.COMPUTE_ENCRYPT_TEXT_ERROR);
        }
    }

    // Decrypt ciphertext.
    // Parameter text Ciphertext to decrypt
    // Returns Decrypted plaintext
    private String decrypt(String text) throws DingTalkEncryptException {
        byte[] originalArr;
        try {
            // Set AES CBC decryption mode
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
            IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);
            // Decode ciphertext with Base64
            byte[] encrypted = Base64.decodeBase64(text);
            // Decrypt
            originalArr = cipher.doFinal(encrypted);
        } catch (Exception e) {
            throw new DingTalkEncryptException(DingTalkEncryptException.COMPUTE_DECRYPT_TEXT_ERROR);
        }

        String plainText;
        String fromCorpid;
        try {
            // Remove padding characters
            byte[] bytes = PKCS7Padding.removePaddingBytes(originalArr);
            // Separate the 16-byte random string, network byte order, and corpId
            byte[] networkOrder = Arrays.copyOfRange(bytes, 16, 20);
            int plainTextLegth = Utils.bytes2int(networkOrder);
            plainText = new String(Arrays.copyOfRange(bytes, 20, 20 + plainTextLegth), CHARSET);
            fromCorpid = new String(Arrays.copyOfRange(bytes, 20 + plainTextLegth, bytes.length),
                    CHARSET);
        } catch (Exception e) {
            throw new DingTalkEncryptException(
                    DingTalkEncryptException.COMPUTE_DECRYPT_TEXT_LENGTH_ERROR);
        }

        // Handle mismatched corpId
        if (!fromCorpid.equals(corpId)) {
            throw new DingTalkEncryptException(
                    DingTalkEncryptException.COMPUTE_DECRYPT_TEXT_CORPID_ERROR);
        }
        return plainText;
    }

    // Digital signature
    // DingTalk mandates SHA-1 over sorted fields; spissatus4j marks SHA-1 as
    // prohibited, so this protocol signature stays on the JDK digest API
    // Parameter token isv token
    // Parameter timestamp Timestamp
    // Parameter nonce Random string
    // Parameter encrypt Encrypted text
    // Throws DingTalkEncryptException
    public String getSignature(String token, String timestamp, String nonce,
                               String encrypt) throws DingTalkEncryptException {
        try {
            String[] array = new String[]{token, timestamp, nonce, encrypt};
            Arrays.sort(array);
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < 4; i++) {
                sb.append(array[i]);
            }
            String str = sb.toString();
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(str.getBytes());
            byte[] digest = md.digest();

            StringBuffer hexstr = new StringBuffer();
            String shaHex = "";
            for (int i = 0; i < digest.length; i++) {
                shaHex = Integer.toHexString(digest[i] & 0xFF);
                if (shaHex.length() < 2) {
                    hexstr.append(0);
                }
                hexstr.append(shaHex);
            }
            return hexstr.toString();
        } catch (Exception e) {
            throw new DingTalkEncryptException(DingTalkEncryptException.COMPUTE_SIGNATURE_ERROR);
        }
    }

    public static class Utils {
        public Utils() {
        }

        public static String getRandomStr(int count) {
            String base = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            Random random = new SecureRandom();
            StringBuffer sb = new StringBuffer();

            for (int i = 0; i < count; ++i) {
                int number = random.nextInt(base.length());
                sb.append(base.charAt(number));
            }

            return sb.toString();
        }

        public static byte[] int2Bytes(int count) {
            byte[] byteArr = new byte[]{(byte) (count >> 24 & 255), (byte) (count >> 16 & 255),
                    (byte) (count >> 8 & 255), (byte) (count & 255)};
            return byteArr;
        }

        public static int bytes2int(byte[] byteArr) {
            int count = 0;

            for (int i = 0; i < 4; ++i) {
                count <<= 8;
                count |= byteArr[i] & 255;
            }

            return count;
        }
    }

    public static class PKCS7Padding {
        private static final Charset CHARSET = Charset.forName("utf-8");
        private static final int BLOCK_SIZE = 32;

        public PKCS7Padding() {
        }

        public static byte[] getPaddingBytes(int count) {
            int amountToPad = 32 - count % 32;
            if (amountToPad == 0) {
                amountToPad = 32;
            }

            char padChr = chr(amountToPad);
            String tmp = new String();

            for (int index = 0; index < amountToPad; ++index) {
                tmp = tmp + padChr;
            }

            return tmp.getBytes(CHARSET);
        }

        public static byte[] removePaddingBytes(byte[] decrypted) {
            int pad = decrypted[decrypted.length - 1];
            if (pad < 1 || pad > 32) {
                pad = 0;
            }

            return Arrays.copyOfRange(decrypted, 0, decrypted.length - pad);
        }

        private static char chr(int a) {
            byte target = (byte) (a & 255);
            return (char) target;
        }
    }

    public static class DingTalkEncryptException extends Exception {
        public static final int SUCCESS = 0;
        public static final int ENCRYPTION_PLAINTEXT_ILLEGAL = 900001;
        public static final int ENCRYPTION_TIMESTAMP_ILLEGAL = 900002;
        public static final int ENCRYPTION_NONCE_ILLEGAL = 900003;
        public static final int AES_KEY_ILLEGAL = 900004;
        public static final int SIGNATURE_NOT_MATCH = 900005;
        public static final int COMPUTE_SIGNATURE_ERROR = 900006;
        public static final int COMPUTE_ENCRYPT_TEXT_ERROR = 900007;
        public static final int COMPUTE_DECRYPT_TEXT_ERROR = 900008;
        public static final int COMPUTE_DECRYPT_TEXT_LENGTH_ERROR = 900009;
        public static final int COMPUTE_DECRYPT_TEXT_CORPID_ERROR = 900010;
        private static Map<Integer, String> msgMap = new HashMap();

        static {
            msgMap.put(0, "成功");
            msgMap.put(900001, "加密明文文本非法");
            msgMap.put(900002, "加密时间戳参数非法");
            msgMap.put(900003, "加密随机字符串参数非法");
            msgMap.put(900005, "签名不匹配");
            msgMap.put(900006, "签名计算失败");
            msgMap.put(900004, "不合法的aes key");
            msgMap.put(900007, "计算加密文字错误");
            msgMap.put(900008, "计算解密文字错误");
            msgMap.put(900009, "计算解密文字长度不匹配");
            msgMap.put(900010, "计算解密文字corpid不匹配");
        }

        private Integer code;

        public DingTalkEncryptException(Integer exceptionCode) {
            super((String) msgMap.get(exceptionCode));
            this.code = exceptionCode;
        }

        public Integer getCode() {
            return this.code;
        }
    }

}
