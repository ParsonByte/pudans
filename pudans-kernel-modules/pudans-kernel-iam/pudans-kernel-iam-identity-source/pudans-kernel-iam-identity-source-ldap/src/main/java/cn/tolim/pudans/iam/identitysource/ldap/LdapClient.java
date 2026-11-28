package cn.tolim.pudans.iam.identitysource.ldap;

import cn.tolim.pudans.iam.identitysource.core.client.AbstractIdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.client.IdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.domain.Dept;
import cn.tolim.pudans.iam.identitysource.core.domain.User;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.LOGGER_NAME;


@Slf4j(topic = LOGGER_NAME)
public class LdapClient extends AbstractIdentitySourceClient<LdapConfig>
        implements IdentitySourceClient {

    private final LdapSearchService searchService;

    public LdapClient(LdapConfig config) {
        super(config);
        this.searchService = new LdapSearchService(config);
    }

    public LdapClient(LdapConfig config, LdapSearchService searchService) {
        super(config);
        this.searchService = searchService;
    }

    @Override
    public Dept getDept(String id) {
        return searchService.getDept(id);
    }

    @Override
    public List<Dept> getDeptList(String id) {
        return searchService.getDeptList(id);
    }

    @Override
    public List<Dept> getDeptList() {
        return searchService.getDeptList();
    }

    @Override
    public User getUser(String id) {
        return searchService.getUser(id);
    }

    @Override
    public List<User> getUserList(String deptId) {
        return searchService.getUserList(deptId);
    }

    @Override
    public List<User> getUserList() {
        return searchService.getUserList();
    }
}