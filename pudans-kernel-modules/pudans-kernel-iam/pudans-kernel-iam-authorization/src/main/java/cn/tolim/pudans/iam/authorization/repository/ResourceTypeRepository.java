package cn.tolim.pudans.iam.authorization.repository;

import cn.tolim.pudans.iam.authorization.entity.ResourceTypeEntity;
import cn.tolim.pudans.iam.authorization.enums.AuthorizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface ResourceTypeRepository extends JpaRepository<ResourceTypeEntity, String> {

    // Returns all resource types declared by an application
    List<ResourceTypeEntity> findAllByAppId(String appId);

    // Returns all resource types declared by an application with the given status
    List<ResourceTypeEntity> findAllByAppIdAndStatus(String appId, AuthorizationStatus status);

    // Returns the resource type of an application, if present
    Optional<ResourceTypeEntity> findByAppIdAndCode(String appId, String code);

    // Returns the resource type with the given code, if present
    Optional<ResourceTypeEntity> findByCode(String code);

    // Checks whether an application declared the given resource type
    boolean existsByAppIdAndCode(String appId, String code);

    // Deletes all resource types declared by an application
    void deleteAllByAppId(String appId);
}