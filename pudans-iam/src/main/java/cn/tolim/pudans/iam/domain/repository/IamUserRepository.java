package cn.tolim.pudans.iam.domain.repository;

import cn.tolim.pudans.iam.domain.entity.IamUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IamUserRepository extends JpaRepository<IamUser, UUID> {

    boolean existsByUsername(String username);
}
