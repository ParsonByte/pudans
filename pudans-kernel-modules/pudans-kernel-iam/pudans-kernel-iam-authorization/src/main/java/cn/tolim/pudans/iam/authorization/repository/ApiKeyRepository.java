package cn.tolim.pudans.iam.authorization.repository;

import cn.tolim.pudans.iam.authorization.entity.ApiKeyEntity;
import cn.tolim.pudans.iam.authorization.enums.AuthorizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, String> {

    // Returns the API key with the given access key ID, if present
    Optional<ApiKeyEntity> findByAccessKeyId(String accessKeyId);

    // Checks whether an API key with the given access key ID exists
    boolean existsByAccessKeyId(String accessKeyId);

    // Returns all API keys owned by a principal
    List<ApiKeyEntity> findAllByPrincipalId(String principalId);

    // Returns all API keys owned by a principal with the given status
    List<ApiKeyEntity> findAllByPrincipalIdAndStatus(String principalId, AuthorizationStatus status);

    // Deletes all API keys owned by a principal
    void deleteAllByPrincipalId(String principalId);
}