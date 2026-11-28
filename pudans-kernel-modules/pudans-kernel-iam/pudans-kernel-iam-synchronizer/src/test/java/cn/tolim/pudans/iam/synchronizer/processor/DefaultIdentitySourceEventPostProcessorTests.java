package cn.tolim.pudans.iam.synchronizer.processor;

import cn.tolim.pudans.iam.communal.entity.account.OrganizationEntity;
import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceEntity;
import cn.tolim.pudans.iam.communal.enums.identitysource.IdentitySourceProvider;
import cn.tolim.pudans.iam.communal.repository.account.OrganizationMemberRepository;
import cn.tolim.pudans.iam.communal.repository.account.OrganizationRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserDetailRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserGroupMemberRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserRepository;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceEventRecordRepository;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceRepository;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceSyncHistoryRepository;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceSyncRecordRepository;
import cn.tolim.pudans.iam.communal.storage.Storage;
import cn.tolim.pudans.iam.core.message.mail.MailMsgEventPublish;
import cn.tolim.pudans.iam.identitysource.core.domain.Dept;
import cn.tolim.pudans.iam.identitysource.core.domain.User;
import cn.tolim.pudans.iam.identitysource.core.enums.IdentitySourceEventReceiveType;
import cn.tolim.pudans.iam.identitysource.core.exception.IdentitySourceNotExistException;
import cn.tolim.pudans.iam.identitysource.core.processor.modal.IdentitySourceEventProcessData;
import cn.tolim.pudans.iam.infra.exception.PudansException;
import cn.tolim.pudans.iam.infra.security.password.PasswordGenerator;
import cn.tolim.pudans.iam.synchronizer.provider.IdentitySourceProviderResolver;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultIdentitySourceEventPostProcessorTests {

    private IdentitySourceRepository identitySourceRepository;
    private RecordingProcessor processor;
    private IdentitySourceEntity identitySource;

    @BeforeEach
    void setUp() {
        identitySourceRepository = mock(IdentitySourceRepository.class);
        identitySource = new IdentitySourceEntity();
        identitySource.setId("source-1");
        identitySource.setName("Source");
        when(identitySourceRepository.findById("source-1"))
                .thenReturn(Optional.of(identitySource));
        processor = new RecordingProcessor(identitySourceRepository);
    }

    private static <T> IdentitySourceEventProcessData<T> eventData(
            List<T> data, IdentitySourceEventReceiveType type) {
        return new IdentitySourceEventProcessData<>("source-1", data,
                IdentitySourceProvider.DINGTALK, LocalDateTime.now(), type);
    }

    @Test
    void shouldRouteUserAddEvent() {
        processor.process(eventData(List.of(new User()), IdentitySourceEventReceiveType.USER_ADD));

        assertEquals(List.of("createUsers"), processor.calls);
    }

    @Test
    void shouldRouteUserModifyEvent() {
        processor.process(eventData(List.of(new User()),
                IdentitySourceEventReceiveType.USER_MODIFY));

        assertEquals(List.of("modifyUsers"), processor.calls);
    }

    @Test
    void shouldRouteUserLeaveEvent() {
        processor.process(eventData(List.of("user-1"),
                IdentitySourceEventReceiveType.USER_LEAVE));

        assertEquals(List.of("leaveUsers"), processor.calls);
    }

    @Test
    void shouldRouteDepartmentCreateEvent() {
        processor.process(eventData(List.of(new Dept()),
                IdentitySourceEventReceiveType.DEPT_CREATE));

        assertEquals(List.of("createOrganizations"), processor.calls);
    }

    @Test
    void shouldRouteDepartmentModifyEvent() {
        processor.process(eventData(List.of(new Dept()),
                IdentitySourceEventReceiveType.DEPT_MODIFY));

        assertEquals(List.of("modifyOrganizations"), processor.calls);
    }

    @Test
    void shouldRouteDepartmentRemoveEvent() {
        processor.process(eventData(List.of("dept-1"),
                IdentitySourceEventReceiveType.DEPT_REMOVE));

        assertEquals(List.of("removeOrganizations"), processor.calls);
    }

    @Test
    void shouldRejectUnsupportedEventType() {
        PudansException exception = assertThrows(PudansException.class,
                () -> processor.process(eventData(List.of("user-1"),
                        IdentitySourceEventReceiveType.USER_REMOVE)));

        assertEquals("钉钉身份提供商事件回调非法事件", exception.getMessage());
        assertEquals(List.of(), processor.calls);
    }

    @Test
    void shouldRejectMissingIdentitySource() {
        when(identitySourceRepository.findById("missing")).thenReturn(Optional.empty());
        IdentitySourceEventProcessData<User> eventData = new IdentitySourceEventProcessData<>(
                "missing", List.of(new User()), IdentitySourceProvider.FEISHU,
                LocalDateTime.now(), IdentitySourceEventReceiveType.USER_ADD);

        assertThrows(IdentitySourceNotExistException.class, () -> processor.process(eventData));
    }

    // Processor that records the dispatched event handler
    private static final class RecordingProcessor extends DefaultIdentitySourceEventPostProcessor {

        private final List<String> calls = new ArrayList<>();

        private RecordingProcessor(IdentitySourceRepository identitySourceRepository) {
            super(mock(MailMsgEventPublish.class), mock(PasswordEncoder.class),
                    mock(PasswordGenerator.class), mock(TransactionDefinition.class),
                    mock(PlatformTransactionManager.class), mock(EntityManager.class),
                    identitySourceRepository, mock(IdentitySourceSyncHistoryRepository.class),
                    mock(IdentitySourceSyncRecordRepository.class), mock(UserRepository.class),
                    mock(OrganizationRepository.class),
                    mock(OrganizationMemberRepository.class),
                    mock(IdentitySourceEventRecordRepository.class), mock(Storage.class),
                    mock(UserDetailRepository.class), mock(UserGroupMemberRepository.class),
                    mock(IdentitySourceProviderResolver.class));
        }

        @Override
        void createUsers(List<User> thirdPartyUserList, LocalDateTime eventTime,
                         IdentitySourceEntity identitySource) {
            calls.add("createUsers");
        }

        @Override
        void modifyUsers(List<User> thirdPartyUserList, LocalDateTime eventTime,
                         IdentitySourceEntity identitySource) {
            calls.add("modifyUsers");
        }

        @Override
        void leaveUsers(List<String> userIds, LocalDateTime eventTime,
                        IdentitySourceEntity identitySource) {
            calls.add("leaveUsers");
        }

        @Override
        void createOrganizations(List<Dept> organizations, LocalDateTime eventTime,
                                 IdentitySourceEntity identitySource) {
            calls.add("createOrganizations");
        }

        @Override
        void modifyOrganizations(List<Dept> organizations, LocalDateTime eventTime,
                                 IdentitySourceEntity identitySource) {
            calls.add("modifyOrganizations");
        }

        @Override
        void removeOrganizations(List<String> organizationIds, LocalDateTime eventTime,
                                 IdentitySourceEntity identitySource) {
            calls.add("removeOrganizations");
        }
    }
}