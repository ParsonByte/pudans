package cn.tolim.pudans.iam.authorization.repository;

import cn.tolim.pudans.iam.authorization.entity.ServiceActionEntity;
import cn.tolim.pudans.iam.authorization.enums.AuthorizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface ServiceActionRepository extends JpaRepository<ServiceActionEntity, String> {

    // Returns all actions declared by an application
    List<ServiceActionEntity> findAllByAppId(String appId);

    // Returns all actions declared by an application with the given status
    List<ServiceActionEntity> findAllByAppIdAndStatus(String appId, AuthorizationStatus status);

    // Returns the action of an application, if present
    Optional<ServiceActionEntity> findByAppIdAndAction(String appId, String action);

    // Returns the action with the given name, if present
    Optional<ServiceActionEntity> findByAction(String action);

    // Checks whether an application declared the given action
    boolean existsByAppIdAndAction(String appId, String action);

    // Deletes all actions declared by an application
    void deleteAllByAppId(String appId);
}