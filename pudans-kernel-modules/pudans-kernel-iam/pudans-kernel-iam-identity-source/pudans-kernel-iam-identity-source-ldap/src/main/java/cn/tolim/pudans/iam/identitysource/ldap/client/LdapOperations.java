package cn.tolim.pudans.iam.identitysource.ldap.client;

import java.util.List;


// Low level LDAP operations used by the identity source
public interface LdapOperations extends AutoCloseable {

    // Search entries below the given base DN
    List<LdapEntry> search(String baseDn, String filter, SearchScope scope, String... attributes);

    // Look up a single entry by distinguished name
    LdapEntry lookup(String dn, String... attributes);

    @Override
    void close();
}