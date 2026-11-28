package cn.tolim.pudans.iam.identitysource.ldap.client;

import javax.naming.directory.SearchControls;
import lombok.Getter;
import lombok.RequiredArgsConstructor;


@Getter
@RequiredArgsConstructor
public enum SearchScope {
    // Search the base entry only
    OBJECT(SearchControls.OBJECT_SCOPE),
    // Search direct children of the base entry
    ONE_LEVEL(SearchControls.ONELEVEL_SCOPE),
    // Search the base entry and all descendants
    SUBTREE(SearchControls.SUBTREE_SCOPE);

    private final int value;
}