package cn.tolim.pudans.iam.identitysource.ldap.client;

import javax.naming.directory.Attributes;


// A single LDAP entry with its distinguished name and attributes
public record LdapEntry(String dn, Attributes attributes) {
}