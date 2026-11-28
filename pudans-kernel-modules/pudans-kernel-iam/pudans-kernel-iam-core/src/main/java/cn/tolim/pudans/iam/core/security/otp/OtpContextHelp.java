package cn.tolim.pudans.iam.core.security.otp;

import cn.tolim.pudans.iam.communal.enums.MailType;
import cn.tolim.pudans.iam.communal.enums.MessageNoticeChannel;
import cn.tolim.pudans.iam.communal.enums.SmsType;
import cn.tolim.pudans.iam.communal.exception.OtpSendException;
import cn.tolim.pudans.iam.core.message.mail.MailMsgEventPublish;
import cn.tolim.pudans.iam.core.message.sms.SmsMsgEventPublish;
import cn.tolim.pudans.iam.infra.exception.PudansException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.boot.cache.autoconfigure.CacheProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static cn.tolim.pudans.iam.core.context.ContextService.getCodeValidTime;
import static cn.tolim.pudans.iam.core.message.MsgVariable.TIME_TO_LIVE;
import static cn.tolim.pudans.iam.infra.constant.IamConstants.COLON;


@Slf4j
@Component
public class OtpContextHelp {

    // Verification code value prefix
    public static final String OTP_CODE_VALUE_PREFIX = "code";
    // Verification interval value prefix
    public static final String OTP_CODE_INTERVAL_PREFIX = "interval";
    // Verification attempt count prefix
    public static final String OTP_CODE_CHECK_COUNT_PREFIX = "check";
    // Verification code sent too frequently; try again later
    private static final String SEND_FREQUENTLY = "发送验证码频繁，请稍候重试";
    // Verification code usage threshold
    private static final long FREQUENCY_THRESHOLD = 3;
    // RedissonClient
    private final RedissonClient redissonClient;
    // Mail message publisher
    private final MailMsgEventPublish mailMsgEventPublish;
    // SMS message publisher
    private final SmsMsgEventPublish smsMsgEventPublish;
    // CacheProperties
    private final CacheProperties cacheProperties;
    public OtpContextHelp(RedissonClient redissonClient, MailMsgEventPublish mailMsgEventPublish,
                          SmsMsgEventPublish smsMsgEventPublish, CacheProperties cacheProperties) {
        this.redissonClient = redissonClient;
        this.mailMsgEventPublish = mailMsgEventPublish;
        this.smsMsgEventPublish = smsMsgEventPublish;
        this.cacheProperties = cacheProperties;
    }

    // Deletes the verification code and its attempt counter.
    private static void removeVerifyCode(RScoredSortedSet<String> verifyCodeScored,
                                         RAtomicLong verifyCodeFrequency) {
        // Delete the verification code
        verifyCodeScored.deleteAsync();
        verifyCodeFrequency.deleteAsync();
    }

    // Sends a verification code through the selected channel.
    public void sendOtp(String recipient, String type, MessageNoticeChannel channel) {
        // Send interval
        RBucket<Boolean> intervalBucket = redissonClient
                .getBucket(getRedisKey(OTP_CODE_INTERVAL_PREFIX, recipient, type, channel));
        if (intervalBucket.isExists()) {
            throw new PudansException(SEND_FREQUENTLY);
        } else {
            // Verification code
            String code = RandomStringUtils.randomNumeric(6);
            RScoredSortedSet<String> verifyCodeScored = redissonClient
                    .getScoredSortedSet(getRedisKey(OTP_CODE_VALUE_PREFIX, recipient, type, channel));
            try {
                removeExpireOtpCode(verifyCodeScored);
                // Expiration time
                Instant expireTime = Instant.now().plus(getCodeValidTime(), ChronoUnit.MINUTES);
                // Store the verification code
                verifyCodeScored.add(expireTime.getEpochSecond(), code);
                verifyCodeScored.expire(expireTime);
                // Send interval (default: 1 minute)
                intervalBucket.set(true, Duration.ofMinutes(TIME_TO_LIVE));
                if (channel == MessageNoticeChannel.MAIL) {
                    // Send email
                    mailMsgEventPublish.publishVerifyCode(recipient, MailType.getType(type), code);
                }
                if (channel == MessageNoticeChannel.SMS) {
                    // Send SMS
                    smsMsgEventPublish.publishVerifyCode(recipient, SmsType.getType(type), code);
                }
                // Verification count
                RAtomicLong verifyCodeFrequency = redissonClient.getAtomicLong(
                        getRedisKey(OTP_CODE_CHECK_COUNT_PREFIX, recipient, type, channel));
                // Initialize verification count to 0
                verifyCodeFrequency.set(0);
                verifyCodeFrequency.expire(expireTime);
            } catch (Exception e) {
                // Delete on send failure
                verifyCodeScored.deleteAsync();
                intervalBucket.deleteAsync();
                throw new OtpSendException(e.getMessage());
            }
        }
    }

    // Removes expired verification codes from the sorted set.
    private void removeExpireOtpCode(RScoredSortedSet<String> verifyCodeScored) {
        int removeCount = verifyCodeScored.removeRangeByScore(Double.POSITIVE_INFINITY,
                Boolean.TRUE, getCurrentTime(), Boolean.TRUE);
        log.debug("删除 OTP 验证码计数: {}", removeCount);
    }

    // Returns the current time in epoch seconds.
    private long getCurrentTime() {
        return System.currentTimeMillis() / 1000;
    }

    // Verifies a code and enforces the maximum attempt count.
    public Boolean checkOtp(String type, MessageNoticeChannel channel, String recipient,
                            String verifyCode) {

        // Verification code
        RScoredSortedSet<String> verifyCodeScored = redissonClient
                .getScoredSortedSet(getRedisKey(OTP_CODE_VALUE_PREFIX, recipient, type, channel));
        // Verification count
        RAtomicLong verifyCodeFrequency = redissonClient
                .getAtomicLong(getRedisKey(OTP_CODE_CHECK_COUNT_PREFIX, recipient, type, channel));
        if (!verifyCodeScored.isExists()) {
            log.info("类型 [{}] 接受者 [{}]，未获取验证码", type, recipient);
            return false;
        }
        if (verifyCodeFrequency.isExists()) {
            long frequency = verifyCodeFrequency.incrementAndGet();
            // Verification count exceeds threshold
            if (frequency > FREQUENCY_THRESHOLD) {
                // Clear verification code and count
                removeVerifyCode(verifyCodeScored, verifyCodeFrequency);
                log.error("类型 [{}] 接受者 [{}]，超出验证次数", type, recipient);
                return false;
            }
        } else {
            return false;
        }
        removeExpireOtpCode(verifyCodeScored);
        // Check verification code
        if (!verifyCodeScored.contains(verifyCode)) {
            log.error("类型 [{}] 接受者 [{}]，验证码不匹配或已过期", type, recipient);
            return false;
        }
        // Verification succeeded; delete code and count
        removeVerifyCode(verifyCodeScored, verifyCodeFrequency);
        return true;
    }

    // Builds the Redis key for the given OTP attribute.
    @NotNull
    private String getRedisKey(String prefix, String recipient, String type,
                               MessageNoticeChannel channel) {
        String keyPrefix = cacheProperties.getRedis().getKeyPrefix();
        return keyPrefix + COLON + "otp" + COLON + prefix + COLON + channel.getCode() + COLON + type
                + COLON + recipient;
    }
}
