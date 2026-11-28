package cn.tolim.pudans.iam.core.security.task;

import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
import cn.tolim.pudans.iam.communal.enums.UserStatus;
import cn.tolim.pudans.iam.communal.repository.account.UserRepository;
import cn.tolim.pudans.iam.infra.lock.Lock;
import cn.tolim.pudans.iam.infra.trace.Trace;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;

import static cn.tolim.pudans.iam.core.context.ContextService.getAutoUnlockTime;


@RequiredArgsConstructor
public class UserUnlockTask {

    private final Logger logger = LoggerFactory.getLogger(UserUnlockTask.class);
    // UserRepository
    private final UserRepository userRepository;

    // Unlock locked users every minute
    @Trace
    @Lock(throwException = false)
    @Scheduled(cron = "0 */1 * * * ?")
    public void execute() {
        logger.info("用户自动解锁任务开始");
        List<UserEntity> list = userRepository.findAllByStatus(UserStatus.LOCKED);
        logger.info("待解锁用户数量: [{}] ", list.size());
        for (UserEntity entity : list) {
            logger.info("开始解锁用户: {}", entity.getUsername());
            LocalDateTime updateTime = entity.getUpdateTime();
            Integer unlockTime = getAutoUnlockTime();
            if (updateTime.plusMinutes(unlockTime).isBefore(LocalDateTime.now())) {
                entity.setStatus(UserStatus.ENABLED);
                userRepository.save(entity);
                logger.info("成功解锁用户: {}", entity.getUsername());
            }
        }
        logger.info("用户自动解锁任务结束");
    }
}
