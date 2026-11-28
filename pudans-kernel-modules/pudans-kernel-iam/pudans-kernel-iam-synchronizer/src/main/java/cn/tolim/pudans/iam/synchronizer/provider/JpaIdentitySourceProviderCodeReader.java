package cn.tolim.pudans.iam.synchronizer.provider;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Component
public class JpaIdentitySourceProviderCodeReader implements IdentitySourceProviderCodeReader {

    // The shared provider enum predates the LDAP and SCIM providers, so the raw column is read to
    // resolve provider codes that the entity converter cannot map
    private static final String PROVIDER_CODES_QUERY =
            "SELECT id_, provider_ FROM pudans_identity_source";

    private final EntityManager entityManager;

    public JpaIdentitySourceProviderCodeReader(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> findAllProviderCodes() {
        List<Object[]> rows = entityManager.createNativeQuery(PROVIDER_CODES_QUERY).getResultList();
        Map<String, String> providerCodes = new HashMap<>(rows.size());
        for (Object[] row : rows) {
            String id = (String) row[0];
            String providerCode = (String) row[1];
            providerCodes.put(id, providerCode);
        }
        return providerCodes;
    }
}