package cn.tolim.pudans.iam.identitysource.core;

import cn.tolim.pudans.iam.communal.enums.TriggerType;
import cn.tolim.pudans.iam.identitysource.core.client.IdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.domain.Dept;
import cn.tolim.pudans.iam.identitysource.core.domain.User;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceEventPostProcessor;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceSyncDeptPostProcessor;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceSyncUserPostProcessor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Getter
public abstract class AbstractDefaultIdentitySource<T extends IdentitySourceConfig>
        implements IdentitySource<T> {

    public static final String URL_JOINER = "/";
    // Identity source provider client
    protected final IdentitySourceClient identitySourceClient;
    // Identity source post-processor
    protected final IdentitySourceSyncUserPostProcessor identitySourceSyncUserPostProcessor;
    // Identity source post-processor
    protected final IdentitySourceSyncDeptPostProcessor identitySourceSyncDeptPostProcessor;
    // Identity source event receive post-processor
    protected final IdentitySourceEventPostProcessor identitySourceEventPostProcessor;
    // Identity source ID
    private final String id;
    // Identity source name
    private final String name;
    // Provider configuration
    private final T config;

    protected AbstractDefaultIdentitySource(String id, String name, T config,
                                            IdentitySourceClient identitySourceClient,
                                            IdentitySourceSyncUserPostProcessor identitySourceSyncUserPostProcessor,
                                            IdentitySourceSyncDeptPostProcessor identitySourceSyncDeptPostProcessor,
                                            IdentitySourceEventPostProcessor identitySourceEventPostProcessor) {
        this.id = id;
        this.name = name;
        this.config = config;
        this.identitySourceClient = identitySourceClient;
        this.identitySourceSyncUserPostProcessor = identitySourceSyncUserPostProcessor;
        this.identitySourceSyncDeptPostProcessor = identitySourceSyncDeptPostProcessor;
        this.identitySourceEventPostProcessor = identitySourceEventPostProcessor;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    // sync
    // Parameter triggerType TriggerType Execution mode
    @Override
    public void sync(TriggerType triggerType) {
        String batch = RandomStringUtils.randomAlphanumeric(7);
        LocalDateTime deptListStartTime = LocalDateTime.now();
        // Pull departments
        List<Dept> deptList = identitySourceClient.getDeptList();
        // Process department information
        identitySourceSyncDeptPostProcessor.process(batch, getId(), deptList, deptListStartTime,
                triggerType);

        // Pull users by department instead of calling getUserList() directly, for performance and data accuracy.
        LocalDateTime userListStartTime = LocalDateTime.now();
        List<User> userList = new ArrayList<>();
        for (Dept dept : deptList) {
            userList.addAll(identitySourceClient.getUserList(dept));
        }
        userList = userList.stream().distinct().collect(Collectors.toList());
        // Process user data
        identitySourceSyncUserPostProcessor.process(batch, getId(), userList, userListStartTime,
                triggerType);
    }

    protected List<User> getUser(String id) {
        List<User> userList = new ArrayList<>();
        userList.add(getIdentitySourceClient().getUser(id));
        return userList;
    }

    protected List<Dept> getDept(String id) {
        List<Dept> deptList = new ArrayList<>();
        deptList.add(getIdentitySourceClient().getDept(id));
        return deptList;
    }

    protected List<String> getIdList(String id) {
        List<String> idList = new ArrayList<>();
        idList.add(id);
        return idList;
    }
}
