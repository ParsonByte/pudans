package cn.tolim.pudans.iam.domain.repository;

import cn.tolim.pudans.iam.domain.entity.IamApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IamApplicationRepository extends JpaRepository<IamApplication, UUID> {
}
