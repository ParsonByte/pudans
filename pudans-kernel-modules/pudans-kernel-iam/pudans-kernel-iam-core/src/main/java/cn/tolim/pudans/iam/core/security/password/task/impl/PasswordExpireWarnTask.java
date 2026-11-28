package cn.tolim.pudans.iam.core.security.password.task.impl;

import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
import cn.tolim.pudans.iam.communal.entity.setting.SettingEntity;
import cn.tolim.pudans.iam.communal.enums.MailType;
import cn.tolim.pudans.iam.communal.enums.SmsType;
import cn.tolim.pudans.iam.communal.enums.UserStatus;
import cn.tolim.pudans.iam.communal.repository.account.UserRepository;
import cn.tolim.pudans.iam.communal.repository.setting.SettingRepository;
import cn.tolim.pudans.iam.core.message.mail.MailMsgEventPublish;
import cn.tolim.pudans.iam.core.message.sms.SmsMsgEventPublish;
import cn.tolim.pudans.iam.core.security.password.task.PasswordExpireTask;
import cn.tolim.pudans.iam.infra.trace.Trace;
import com.google.common.collect.Maps;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Example;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import static cn.tolim.pudans.iam.core.message.MsgVariable.EXPIRE_DAYS;
import static cn.tolim.pudans.iam.core.setting.PasswordPolicySettingConstants.*;


@RequiredArgsConstructor
public class PasswordExpireWarnTask implements PasswordExpireTask {
    private final Logger logger = LoggerFactory.getLogger(PasswordExpireWarnTask.class);
    // Set
    private final SettingRepository settingRepository;
    // User repository
    private final UserRepository userRepository;
    // Mail message publisher
    private final MailMsgEventPublish mailMsgEventPublish;
    // SMS message sender
    private final SmsMsgEventPublish smsMsgEventPublish;

    // Reminder: scan every day at midnight and send email and SMS reminders
    @Trace
    @Scheduled(cron = "0 0 0 * * ?")
    @Override
    public void execute() {
        logger.info("密码过期前提醒任务开始");
        int expireDays = getExpireDays();
        int expireWarnDays = getWarnExpireDays();
        // 1. Query passwords nearing expiration by reminder time with pagination
        List<UserEntity> list = userRepository
                .findAll(Example.of(new UserEntity().setStatus(UserStatus.ENABLED)));
        // 2. Send notification reminders
        for (UserEntity user : list) {
            // Get expiration date
            LocalDate expiredDate = user.getLastUpdatePasswordTime().atOffset(ZoneOffset.MAX)
                    .plusDays(expireDays).toLocalDate();
            // Reminder time reached
            if (user.getLastUpdatePasswordTime().plusDays(expireWarnDays)
                    .isBefore(LocalDateTime.now())) {
                // If email is not empty, send an email notification
                if (StringUtils.isNotBlank(user.getEmail())) {
                    logger.info("邮件通知密码过期前提醒用户:{}", user.getUsername());
                    HashMap<String, Object> map = Maps.newHashMap();
                    // Get the interval between two times
                    map.put(EXPIRE_DAYS, Duration.between(LocalDate.now(), expiredDate).toDays());
                    mailMsgEventPublish.publish(MailType.PASSWORD_SOON_EXPIRED_REMIND,
                            user.getEmail(), map);
                }
                // SMS notification
                if (StringUtils.isNotBlank(user.getPhone())) {
                    logger.info("短信通知密码过期前提醒用户:{}", user.getUsername());
                    LinkedHashMap<String, String> map = Maps.newLinkedHashMap();
                    // Get the interval between two times
                    map.put(EXPIRE_DAYS,
                            String.valueOf(Duration.between(LocalDate.now(), expiredDate).toDays()));
                    smsMsgEventPublish.publish(SmsType.PASSWORD_SOON_EXPIRED_REMIND,
                            user.getPhone(), map);
                }
            }

        }
        logger.info("密码过期前提醒任务结束");
    }

    // Get warning days before password expiration
    // Returns: Integer
    private Integer getWarnExpireDays() {
        SettingEntity beforeExpireDaysSetting = settingRepository
                .findByName(PASSWORD_POLICY_VALID_WARN_BEFORE_DAYS);
        String beforeExpireDays = Objects.isNull(beforeExpireDaysSetting)
                ? PASSWORD_POLICY_DEFAULT_SETTINGS.get(PASSWORD_POLICY_VALID_WARN_BEFORE_DAYS)
                : beforeExpireDaysSetting.getValue();
        return Integer.valueOf(beforeExpireDays);
    }

    // Get password expiration date
    // Returns: Integer
    private Integer getExpireDays() {
        SettingEntity expireDaysSetting = settingRepository.findByName(PASSWORD_POLICY_VALID_DAYS);
        String expireDays = Objects.isNull(expireDaysSetting)
                ? PASSWORD_POLICY_DEFAULT_SETTINGS.get(PASSWORD_POLICY_VALID_DAYS)
                : expireDaysSetting.getValue();
        return Integer.parseInt(expireDays);
    }
}
