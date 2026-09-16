package cn.tolim.pudans.security.authorization;

import java.util.Collection;

public class PatScopeDeniedException extends RuntimeException {

    public PatScopeDeniedException(String resource, String verb, Collection<String> grantedScopes) {
        super("Personal access token scope does not allow [" + resource + ':' + verb + "], granted scopes "
                + grantedScopes);
    }
}
