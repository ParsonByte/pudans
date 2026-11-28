package cn.tolim.pudans.iam.domain.repository;

import cn.tolim.pudans.iam.domain.entity.IamOrganization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IamOrganizationRepository extends JpaRepository<IamOrganization, UUID> {
}
