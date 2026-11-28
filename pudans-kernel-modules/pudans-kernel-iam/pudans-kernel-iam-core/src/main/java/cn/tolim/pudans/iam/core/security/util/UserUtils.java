package cn.tolim.pudans.iam.core.security.util;

import cn.tolim.pudans.iam.communal.entity.account.UserDetailEntity;
import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
import cn.tolim.pudans.iam.communal.exception.UserNotFoundException;
import cn.tolim.pudans.iam.communal.repository.account.UserDetailRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserRepository;
import cn.tolim.pudans.iam.infra.context.ApplicationContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;


public final class UserUtils {
    private static final Logger logger = LoggerFactory.getLogger(UserUtils.class);

    public static UserEntity getUser() {
        return getUser(cn.tolim.pudans.iam.infra.security.util.SecurityUtils.getCurrentUserId());
    }

    public static UserEntity getUser(String userId) {
        Optional<UserEntity> optional = ApplicationContextService.getBean(UserRepository.class)
                .findById(userId);
        if (optional.isPresent()) {
            return optional.get();
        }
        SecurityContextHolder.clearContext();
        logger.error("根据用户ID: [{}] 未查询到用户信息", userId);
        throw new UserNotFoundException();
    }

    public static UserDetailEntity getUserDetails() {
        return getUserDetails(
                cn.tolim.pudans.iam.infra.security.util.SecurityUtils.getCurrentUserId());
    }

    public static UserDetailEntity getUserDetails(String userId) {
        Optional<UserDetailEntity> optional = ApplicationContextService
                .getBean(UserDetailRepository.class).findByUserId(userId);
        if (optional.isPresent()) {
            return optional.get();
        }
        SecurityContextHolder.clearContext();
        logger.error("根据用户ID: [{}] 未查询到用户信息", userId);
        throw new UserNotFoundException();
    }
}
