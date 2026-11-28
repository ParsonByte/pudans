package cn.tolim.pudans.iam.identitysource.ldap.client;

import cn.tolim.pudans.iam.identitysource.ldap.LdapConfig;


public class DefaultLdapConnectionFactory implements LdapConnectionFactory {

    @Override
    public LdapOperations open(LdapConfig config) {
        return new JndiLdapOperations(config);
    }
}