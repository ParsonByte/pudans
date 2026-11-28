package cn.tolim.pudans.iam.core.security.password.task.impl;

import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
import cn.tolim.pudans.iam.communal.entity.setting.SettingEntity;
import cn.tolim.pudans.iam.communal.enums.UserStatus;
import cn.tolim.pudans.iam.communal.repository.account.UserRepository;
import cn.tolim.pudans.iam.communal.repository.setting.SettingRepository;
import cn.tolim.pudans.iam.core.security.password.task.PasswordExpireTask;
import cn.tolim.pudans.iam.infra.trace.Trace;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static cn.tolim.pudans.iam.core.setting.PasswordPolicySettingConstants.PASSWORD_POLICY_DEFAULT_SETTINGS;
import static cn.tolim.pudans.iam.core.setting.PasswordPolicySettingConstants.PASSWORD_POLICY_VALID_DAYS;


@RequiredArgsConstructor
public class PasswordExpireLockTask implements PasswordExpireTask {

    private final Logger logger = LoggerFactory.getLogger(PasswordExpireLockTask.class);
    // Set
    private final SettingRepository settingRepository;
    // UserRepository
    private final UserRepository userRepository;

    // Lock users with expired passwords
    @Trace
    @Scheduled(cron = "0 0 0 * * ?")
    @Override
    public void execute() {
        logger.info("密码过期锁定用户任务开始");
        // Query users not locked for password expiration or expiration lock
        List<UserEntity> list = userRepository.findAllByStatusIn(
                Lists.newArrayList(UserStatus.PASSWORD_EXPIRED_LOCKED, UserStatus.EXPIRED_LOCKED));
        Iterator<UserEntity> iterator = list.iterator();
        while (!list.isEmpty()) {
            UserEntity user = iterator.next();
            // Get expiration date
            int expireDays = getExpireDays();
            if (user.getLastUpdatePasswordTime().plusDays(expireDays)
                    .isBefore(LocalDateTime.now())) {
                user.setStatus(UserStatus.PASSWORD_EXPIRED_LOCKED);
                userRepository.save(user);
                logger.info("锁定密码过期用户:{}", user.getUsername());
                iterator.remove();
            }
            iterator = list.iterator();
        }
        logger.info("密码过期锁定用户任务结束");
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
