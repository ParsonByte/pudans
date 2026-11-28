package cn.tolim.pudans.iam.authentication.webauthn.repository;

import cn.tolim.pudans.iam.authentication.webauthn.entity.WebAuthnCredentialEntity;
import cn.tolim.pudans.iam.authentication.webauthn.enums.WebAuthnCredentialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// Persistence of WebAuthn credentials
@Repository
public interface WebAuthnCredentialRepository
        extends JpaRepository<WebAuthnCredentialEntity, String> {

    // Finds a credential by its Base64URL credential ID
    Optional<WebAuthnCredentialEntity> findByCredentialId(String credentialId);

    // Finds a credential by its owner and credential ID
    Optional<WebAuthnCredentialEntity> findByCredentialIdAndUserId(String credentialId,
                                                                   String userId);

    // Finds all credentials of a user
    List<WebAuthnCredentialEntity> findAllByUserIdOrderByCreateTimeDesc(String userId);

    // Finds active credentials of a user
    List<WebAuthnCredentialEntity> findAllByUserIdAndStatusOrderByCreateTimeDesc(
            String userId, WebAuthnCredentialStatus status);

    // Counts credentials of a user
    long countByUserId(String userId);

    // Checks whether a credential ID already exists
    boolean existsByCredentialId(String credentialId);

    // Deletes all credentials of one user
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteAllByUserId(String userId);
}