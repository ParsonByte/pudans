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

import java.util.Iterator;
import java.util.List;


@RequiredArgsConstructor
public class UserExpireLockTask {

    private final Logger logger = LoggerFactory.getLogger(UserExpireLockTask.class);
    // UserRepository
    private final UserRepository userRepository;

    // Locks expired users every day at 00:00.
    @Trace
    @Lock(throwException = false)
    @Scheduled(cron = "0 0 0 * * ?")
    public void execute() {
        logger.info("扫描已过期待锁定用户任务开始");
        // Query all expired users
        List<UserEntity> list = userRepository.findExpireUser();
        Iterator<UserEntity> iterator = list.iterator();
        logger.info("已过期待锁定用户数量为:{}个", list.size());
        while (!list.isEmpty()) {
            UserEntity entity = iterator.next();
            entity.setStatus(UserStatus.EXPIRED_LOCKED);
            userRepository.save(entity);
            logger.info("锁定已过期用户：{}", entity.getUsername());
            iterator.remove();
            iterator = list.iterator();
        }
        logger.info("扫描已过期待锁定任务结束");
    }

}
