package cn.tolim.pudans.iam.identitysource.ldap.client;

import cn.tolim.pudans.iam.identitysource.ldap.LdapConfig;


// Opens LDAP connections for an identity source
public interface LdapConnectionFactory {

    // Open a new operations handle
    LdapOperations open(LdapConfig config);
}