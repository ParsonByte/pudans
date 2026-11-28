package cn.tolim.pudans.iam.identitysource.scim.client;

import cn.tolim.pudans.iam.identitysource.core.client.AbstractIdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.client.IdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.domain.Dept;
import cn.tolim.pudans.iam.identitysource.core.domain.User;
import cn.tolim.pudans.iam.identitysource.core.exception.ApiCallException;
import cn.tolim.pudans.iam.identitysource.scim.ScimConfig;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimGroup;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimGroupListResponse;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimGroupMember;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimUser;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimUserListResponse;
import cn.tolim.pudans.iam.identitysource.scim.mapper.ScimMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.LOGGER_NAME;
import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.MEMBER_TYPE_GROUP;


@Slf4j(topic = LOGGER_NAME)
public class ScimClient extends AbstractIdentitySourceClient<ScimConfig>
        implements IdentitySourceClient {

    private final ScimHttpClient httpClient;
    private final ScimMapper mapper;

    public ScimClient(ScimConfig config) {
        super(config);
        this.httpClient = new ScimHttpClient(config);
        this.mapper = new ScimMapper(config);
    }

    public ScimClient(ScimConfig config, ScimHttpClient httpClient) {
        super(config);
        this.httpClient = httpClient;
        this.mapper = new ScimMapper(config);
    }

    @Override
    public Dept getDept(String id) {
        if (isRootDept(id)) {
            return rootDept();
        }
        return mapper.toDept(httpClient.getGroup(id));
    }

    @Override
    public List<Dept> getDeptList(String id) {
        if (isRootDept(id)) {
            return getDeptList();
        }
        return List.of(getDept(id));
    }

    @Override
    public List<Dept> getDeptList() {
        List<Dept> deptList = new ArrayList<>();
        deptList.add(rootDept());
        if (getConfig().isGroupSyncEnabled()) {
            listAllGroups().forEach(group -> deptList.add(mapper.toDept(group)));
        }
        return deptList;
    }

    @Override
    public User getUser(String id) {
        return mapper.toUser(httpClient.getUser(id));
    }

    @Override
    public List<User> getUserList(String deptId) {
        if (isRootDept(deptId)) {
            return getUserList();
        }
        ScimGroup group = httpClient.getGroup(deptId);
        List<User> users = new ArrayList<>();
        if (group.getMembers() == null) {
            return users;
        }
        for (ScimGroupMember member : group.getMembers()) {
            if (MEMBER_TYPE_GROUP.equalsIgnoreCase(member.getType())
                    || StringUtils.isBlank(member.getValue())) {
                continue;
            }
            try {
                users.add(mapper.toUser(httpClient.getUser(member.getValue())));
            } catch (ApiCallException exception) {
                log.warn("Skipping SCIM group member [{}] of group [{}]: {}", member.getValue(),
                        deptId, exception.getMessage());
            }
        }
        return users;
    }

    @Override
    public List<User> getUserList() {
        List<User> users = new ArrayList<>();
        for (ScimUser scimUser : listAllUsers()) {
            ScimUser representation = scimUser;
            if (getConfig().isDetailOnListEnabled() && StringUtils.isNotBlank(scimUser.getId())) {
                // Fetch the full resource so every page produces an identical representation
                representation = httpClient.getUser(scimUser.getId());
            }
            users.add(mapper.toUser(representation));
        }
        return users;
    }

    // Whether the identifier refers to the synthetic root department
    public boolean isRootDept(String id) {
        return StringUtils.equals(getConfig().getEffectiveRootDeptId(), id);
    }

    // Build the synthetic root department
    private Dept rootDept() {
        Dept dept = new Dept();
        dept.setDeptId(getConfig().getEffectiveRootDeptId());
        dept.setName(getConfig().getEffectiveRootDeptName());
        dept.setOrder(0L);
        return dept;
    }

    // Read every user page from the SCIM server
    private List<ScimUser> listAllUsers() {
        List<ScimUser> users = new ArrayList<>();
        int startIndex = 1;
        int pageSize = getConfig().getEffectivePageSize();
        while (true) {
            ScimUserListResponse page = httpClient.listUsers(startIndex, pageSize);
            List<ScimUser> resources = page.getResources();
            if (resources == null || resources.isEmpty()) {
                break;
            }
            users.addAll(resources);
            startIndex += resources.size();
            if (isLastPage(page.getTotalResults(), startIndex, resources.size(), pageSize)) {
                break;
            }
        }
        return users;
    }

    // Read every group page from the SCIM server
    private List<ScimGroup> listAllGroups() {
        List<ScimGroup> groups = new ArrayList<>();
        int startIndex = 1;
        int pageSize = getConfig().getEffectivePageSize();
        while (true) {
            ScimGroupListResponse page = httpClient.listGroups(startIndex, pageSize);
            List<ScimGroup> resources = page.getResources();
            if (resources == null || resources.isEmpty()) {
                break;
            }
            groups.addAll(resources);
            startIndex += resources.size();
            if (isLastPage(page.getTotalResults(), startIndex, resources.size(), pageSize)) {
                break;
            }
        }
        return groups;
    }

    // Whether the pagination has reached the last page
    private boolean isLastPage(Integer totalResults, int nextStartIndex, int pageCount,
                               int pageSize) {
        if (totalResults != null && nextStartIndex > totalResults) {
            return true;
        }
        return pageCount < pageSize;
    }
}