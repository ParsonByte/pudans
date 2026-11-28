package cn.tolim.pudans.iam.identitysource.ldap.client;

import cn.tolim.pudans.iam.identitysource.core.exception.ApiCallException;
import cn.tolim.pudans.iam.identitysource.ldap.LdapConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.LOGGER_NAME;


@Slf4j(topic = LOGGER_NAME)
public class JndiLdapOperations implements LdapOperations {

    private static final String LDAP_CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    private static final String CONNECT_TIMEOUT_PROPERTY = "com.sun.jndi.ldap.connect.timeout";
    private static final String READ_TIMEOUT_PROPERTY = "com.sun.jndi.ldap.read.timeout";

    private final LdapConfig config;
    private LdapContext context;

    public JndiLdapOperations(LdapConfig config) {
        this.config = config;
    }

    @Override
    public List<LdapEntry> search(String baseDn, String filter, SearchScope scope,
                                  String... attributes) {
        try {
            LdapContext ldapContext = getContext();
            SearchControls controls = buildSearchControls(scope, attributes);
            List<LdapEntry> entries = new ArrayList<>();
            int pageSize = config.getPageSize() == null ? 0 : config.getPageSize();
            byte[] cookie = null;
            if (pageSize > 0) {
                ldapContext.setRequestControls(new Control[]{
                        new PagedResultsControl(pageSize, Control.NONCRITICAL)});
            }
            do {
                NamingEnumeration<SearchResult> results = ldapContext.search(baseDn, filter, controls);
                try {
                    while (results.hasMore()) {
                        SearchResult result = results.next();
                        entries.add(new LdapEntry(resolveDn(baseDn, result),
                                result.getAttributes()));
                    }
                } finally {
                    results.close();
                }
                if (pageSize <= 0) {
                    break;
                }
                cookie = readCookie(ldapContext.getResponseControls());
                if (cookie == null || cookie.length == 0) {
                    break;
                }
                ldapContext.setRequestControls(new Control[]{
                        new PagedResultsControl(pageSize, cookie, Control.NONCRITICAL)});
            } while (true);
            if (pageSize > 0) {
                ldapContext.setRequestControls(null);
            }
            return entries;
        } catch (NamingException | IOException exception) {
            closeOnFailure();
            log.error("LDAP search failed on base [{}], filter [{}]: {}", baseDn, filter,
                    exception.getMessage());
            throw new ApiCallException("LDAP search failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public LdapEntry lookup(String dn, String... attributes) {
        try {
            Attributes result = getContext().getAttributes(dn, attributes);
            return new LdapEntry(dn, result);
        } catch (NamingException exception) {
            closeOnFailure();
            log.error("LDAP lookup failed on [{}]: {}", dn, exception.getMessage());
            throw new ApiCallException("LDAP lookup failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public void close() {
        if (context != null) {
            try {
                context.close();
            } catch (NamingException exception) {
                log.debug("Closing LDAP context failed: {}", exception.getMessage());
            } finally {
                context = null;
            }
        }
    }

    // Get or create the LDAP context
    private synchronized LdapContext getContext() throws NamingException {
        if (context == null) {
            context = createContext();
        }
        return context;
    }

    // Create a new LDAP context from the identity source configuration
    private LdapContext createContext() throws NamingException {
        Hashtable<String, Object> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_CONTEXT_FACTORY);
        environment.put(Context.PROVIDER_URL, config.getServerUrl());
        environment.put(Context.REFERRAL, StringUtils.defaultIfBlank(config.getReferral(), "ignore"));
        if (StringUtils.isNotBlank(config.getBindDn())) {
            environment.put(Context.SECURITY_AUTHENTICATION, "simple");
            environment.put(Context.SECURITY_PRINCIPAL, config.getBindDn());
            environment.put(Context.SECURITY_CREDENTIALS,
                    StringUtils.defaultString(config.getBindPassword()));
        } else {
            environment.put(Context.SECURITY_AUTHENTICATION, "none");
        }
        if (config.getConnectTimeout() != null) {
            environment.put(CONNECT_TIMEOUT_PROPERTY, String.valueOf(config.getConnectTimeout()));
        }
        if (config.getReadTimeout() != null) {
            environment.put(READ_TIMEOUT_PROPERTY, String.valueOf(config.getReadTimeout()));
        }
        InitialLdapContext ldapContext = new InitialLdapContext(environment, null);
        if (config.isStartTlsEnabled()) {
            negotiateStartTls(ldapContext);
        }
        log.debug("LDAP connection established to {}", config.getServerUrl());
        return ldapContext;
    }

    // Negotiate StartTLS on an established LDAP context
    private void negotiateStartTls(LdapContext ldapContext) throws NamingException {
        try {
            StartTlsResponse tls = (StartTlsResponse) ldapContext.extendedOperation(new StartTlsRequest());
            tls.negotiate();
        } catch (IOException exception) {
            try {
                ldapContext.close();
            } catch (NamingException closeException) {
                log.debug("Closing LDAP context after StartTLS failure failed: {}",
                        closeException.getMessage());
            }
            throw new NamingException("LDAP StartTLS negotiation failed: " + exception.getMessage());
        }
    }

    // Build search controls for the requested scope and attributes
    private SearchControls buildSearchControls(SearchScope scope, String... attributes) {
        SearchControls controls = new SearchControls();
        controls.setSearchScope(scope.getValue());
        controls.setReturningObjFlag(false);
        controls.setDerefLinkFlag(false);
        if (attributes != null && attributes.length > 0) {
            controls.setReturningAttributes(attributes);
        }
        return controls;
    }

    // Resolve the absolute distinguished name of a search result
    private String resolveDn(String baseDn, SearchResult result) {
        try {
            String dn = result.getNameInNamespace();
            if (StringUtils.isNotBlank(dn)) {
                return dn;
            }
        } catch (UnsupportedOperationException | IllegalStateException exception) {
            log.debug("Resolving relative LDAP entry name: {}", exception.getMessage());
        }
        String name = result.getName();
        if (StringUtils.isBlank(name)) {
            return baseDn;
        }
        return StringUtils.isBlank(baseDn) ? name : name + "," + baseDn;
    }

    // Read the paged results cookie from response controls
    private byte[] readCookie(Control[] responseControls) {
        if (responseControls == null) {
            return null;
        }
        for (Control control : responseControls) {
            if (control instanceof PagedResultsResponseControl pagedControl) {
                return pagedControl.getCookie();
            }
        }
        return null;
    }

    // Close the context after a failure so the next call reconnects
    private void closeOnFailure() {
        close();
    }
}