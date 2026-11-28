package cn.tolim.pudans.iam.identitysource.ldap;

import cn.tolim.pudans.iam.identitysource.core.domain.Dept;
import cn.tolim.pudans.iam.identitysource.core.domain.User;
import cn.tolim.pudans.iam.identitysource.core.exception.ApiCallException;
import cn.tolim.pudans.iam.identitysource.ldap.client.DefaultLdapConnectionFactory;
import cn.tolim.pudans.iam.identitysource.ldap.client.LdapConnectionFactory;
import cn.tolim.pudans.iam.identitysource.ldap.client.LdapEntry;
import cn.tolim.pudans.iam.identitysource.ldap.client.LdapOperations;
import cn.tolim.pudans.iam.identitysource.ldap.client.SearchScope;
import cn.tolim.pudans.iam.identitysource.ldap.util.LdapFilterUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import javax.naming.directory.Attributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.LOGGER_NAME;


@Slf4j(topic = LOGGER_NAME)
public class LdapSearchService {

    private final LdapConfig config;
    private final LdapConnectionFactory connectionFactory;
    private final LdapAttributeMapper mapper;

    public LdapSearchService(LdapConfig config) {
        this(config, new DefaultLdapConnectionFactory());
    }

    public LdapSearchService(LdapConfig config, LdapConnectionFactory connectionFactory) {
        this.config = config;
        this.connectionFactory = connectionFactory;
        this.mapper = new LdapAttributeMapper(config);
    }

    // Verify the connection and the base DN used by searches
    public void testConnection() {
        try (LdapOperations operations = connectionFactory.open(config)) {
            operations.search(config.getBaseDn(), "(objectClass=*)", SearchScope.OBJECT,
                    "objectClass");
        }
    }

    // All departments, derived from LDAP groups plus an optional synthetic root
    public List<Dept> getDeptList() {
        List<Dept> deptList = new ArrayList<>();
        if (config.isRootDeptEnabled()) {
            deptList.add(rootDept());
        }
        try (LdapOperations operations = connectionFactory.open(config)) {
            List<LdapEntry> entries = operations.search(config.getGroupBaseDn(),
                    config.getGroupSearchFilter(), SearchScope.SUBTREE, groupAttributes());
            entries.forEach(entry -> deptList.add(mapper.toDept(entry)));
        }
        return deptList;
    }

    // Department by identifier, which is either the synthetic root or a group DN
    public Dept getDept(String id) {
        if (isRootDept(id)) {
            return rootDept();
        }
        try (LdapOperations operations = connectionFactory.open(config)) {
            return findDept(operations, id);
        }
    }

    // The department itself and its descendants, groups are flat in LDAP
    public List<Dept> getDeptList(String id) {
        if (isRootDept(id)) {
            return getDeptList();
        }
        return List.of(getDept(id));
    }

    // User by external identifier
    public User getUser(String id) {
        try (LdapOperations operations = connectionFactory.open(config)) {
            String filter = LdapFilterUtils.and(config.getUserSearchFilter(),
                    LdapFilterUtils.equality(config.getUserIdAttribute(), id));
            List<LdapEntry> entries = operations.search(config.getUserBaseDn(), filter,
                    SearchScope.SUBTREE, userAttributes());
            if (CollectionUtils.isEmpty(entries)) {
                throw new ApiCallException("LDAP user not found: " + id);
            }
            LdapEntry entry = entries.getFirst();
            User user = mapper.toUser(entry);
            populateGroupMemberships(operations, entry, user);
            return user;
        }
    }

    // Users belonging to a department, the synthetic root returns all users
    public List<User> getUserList(String deptId) {
        try (LdapOperations operations = connectionFactory.open(config)) {
            if (isRootDept(deptId)) {
                return findAllUsers(operations);
            }
            LdapEntry group = findDeptEntry(operations, deptId);
            List<User> users = findGroupMembers(operations, group);
            if (users.isEmpty() && StringUtils.isNotBlank(config.getMemberOfAttribute())) {
                users = findUsersByMemberOf(operations, group.dn());
            }
            return users;
        }
    }

    // All users below the configured user search base
    public List<User> getUserList() {
        try (LdapOperations operations = connectionFactory.open(config)) {
            return findAllUsers(operations);
        }
    }

    // Whether the identifier refers to the synthetic root department
    public boolean isRootDept(String id) {
        return config.isRootDeptEnabled() && StringUtils.equals(config.getEffectiveRootDeptId(), id);
    }

    // Build the synthetic root department
    private Dept rootDept() {
        Dept dept = new Dept();
        dept.setDeptId(config.getEffectiveRootDeptId());
        dept.setName(config.getEffectiveRootDeptName());
        dept.setOrder(0L);
        return dept;
    }

    // Search all users and attach their group memberships
    private List<User> findAllUsers(LdapOperations operations) {
        List<LdapEntry> entries = operations.search(config.getUserBaseDn(),
                config.getUserSearchFilter(), SearchScope.SUBTREE, userAttributes());
        List<User> users = new ArrayList<>();
        for (LdapEntry entry : entries) {
            User user = mapper.toUser(entry);
            populateGroupMemberships(operations, entry, user);
            users.add(user);
        }
        return users;
    }

    // Resolve a department from a group DN or a group name
    private Dept findDept(LdapOperations operations, String id) {
        return mapper.toDept(findDeptEntry(operations, id));
    }

    // Resolve a group entry from a DN or a group name
    private LdapEntry findDeptEntry(LdapOperations operations, String id) {
        if (StringUtils.contains(id, "=")) {
            return operations.lookup(id, groupAttributes());
        }
        String filter = LdapFilterUtils.and(config.getGroupSearchFilter(),
                LdapFilterUtils.equality(config.getGroupNameAttribute(), id));
        List<LdapEntry> entries = operations.search(config.getGroupBaseDn(), filter,
                SearchScope.SUBTREE, groupAttributes());
        if (CollectionUtils.isEmpty(entries)) {
            throw new ApiCallException("LDAP group not found: " + id);
        }
        return entries.getFirst();
    }

    // Resolve the users referenced by a group's member attribute
    private List<User> findGroupMembers(LdapOperations operations, LdapEntry group) {
        List<String> memberDns = mapper.getMemberDns(group);
        List<User> users = new ArrayList<>();
        for (String memberDn : memberDns) {
            try {
                LdapEntry entry = operations.lookup(memberDn, userAttributes());
                if (isGroupEntry(entry.attributes())) {
                    // Nested groups are not supported as users
                    continue;
                }
                User user = mapper.toUser(entry);
                populateGroupMemberships(operations, entry, user);
                users.add(user);
            } catch (ApiCallException exception) {
                log.warn("Skipping LDAP group member [{}] of group [{}]: {}", memberDn,
                        group.dn(), exception.getMessage());
            }
        }
        return users;
    }

    // Search users that reference the group through the memberOf attribute
    private List<User> findUsersByMemberOf(LdapOperations operations, String groupDn) {
        String filter = LdapFilterUtils.and(config.getUserSearchFilter(),
                LdapFilterUtils.equality(config.getMemberOfAttribute(), groupDn));
        List<LdapEntry> entries = operations.search(config.getUserBaseDn(), filter,
                SearchScope.SUBTREE, userAttributes());
        List<User> users = new ArrayList<>();
        for (LdapEntry entry : entries) {
            User user = mapper.toUser(entry);
            populateGroupMemberships(operations, entry, user);
            users.add(user);
        }
        return users;
    }

    // Fill missing group memberships through memberOf or a group member search
    private void populateGroupMemberships(LdapOperations operations, LdapEntry entry, User user) {
        Set<String> groupDns = new LinkedHashSet<>();
        if (!CollectionUtils.isEmpty(user.getDeptIdList())) {
            groupDns.addAll(user.getDeptIdList());
        }
        if (groupDns.isEmpty() && StringUtils.isNotBlank(config.getGroupMemberAttribute())) {
            String filter = LdapFilterUtils.and(config.getGroupSearchFilter(),
                    LdapFilterUtils.equality(config.getGroupMemberAttribute(), entry.dn()));
            List<LdapEntry> groups = operations.search(config.getGroupBaseDn(), filter,
                    SearchScope.SUBTREE, "objectClass");
            groups.forEach(group -> groupDns.add(group.dn()));
        }
        user.setDeptIdList(new ArrayList<>(groupDns));
    }

    // Whether an entry has a group object class
    private boolean isGroupEntry(Attributes attributes) {
        return LdapAttributeMapper.isGroup(attributes);
    }

    // Attributes requested for user searches
    private String[] userAttributes() {
        List<String> attributes = new ArrayList<>();
        attributes.add(config.getUserIdAttribute());
        attributes.add(config.getUserNameAttribute());
        attributes.add(config.getUserDisplayNameAttribute());
        attributes.add(config.getUserEmailAttribute());
        attributes.add(config.getUserPhoneAttribute());
        attributes.add(config.getUserAvatarAttribute());
        attributes.add(config.getUserActiveAttribute());
        attributes.add(config.getMemberOfAttribute());
        return attributes.stream().filter(StringUtils::isNotBlank).distinct()
                .toArray(String[]::new);
    }

    // Attributes requested for group searches
    private String[] groupAttributes() {
        List<String> attributes = new ArrayList<>();
        attributes.add(config.getGroupNameAttribute());
        attributes.add(config.getGroupMemberAttribute());
        attributes.add("objectClass");
        return attributes.stream().filter(StringUtils::isNotBlank).distinct()
                .toArray(String[]::new);
    }
}