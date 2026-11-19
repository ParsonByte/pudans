package cn.tolim.pudans.core.ai.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.contract.core.extension.ai.AiTeam;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.infra.exception.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiTeamServiceTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    AiSettingService settingService;

    @Mock
    UserService userService;

    private AiTeamService service;

    @BeforeEach
    void setUp() {
        service = new AiTeamService(client, settingService, userService);
    }

    @Test
    void recognizesAnActiveMember() {
        when(client.get(AiTeam.class, "team-a")).thenReturn(Mono.just(team("team-a", "alice")));

        StepVerifier.create(service.isMember("team-a", "alice"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void defaultTeamIsSharedWithEveryAuthenticatedUser() {
        when(client.get(AiTeam.class, "default")).thenReturn(Mono.just(team("default", "system")));

        StepVerifier.create(service.isMember("default", "carol"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void anUnknownTeamHasNoMembers() {
        when(client.get(AiTeam.class, "missing")).thenReturn(Mono.empty());

        StepVerifier.create(service.isMember("missing", "carol"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void rejectsAnAgentInAPrivateVisibilityForAnotherUser() {
        var agent = agent("agent-a", "team-a", "bob", AiAgent.Visibility.PRIVATE);
        when(client.get(AiAgent.class, "agent-a")).thenReturn(Mono.just(agent));
        when(client.get(AiTeam.class, "team-a")).thenReturn(Mono.just(team("team-a", "alice")));

        StepVerifier.create(service.requireAgent("agent-a", "alice"))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void defaultAgentUsesTheSettingDefaults() {
        var setting = new AiSetting();
        setting.getSpec().getDefaults().setAgent("agent-a");
        when(settingService.defaults()).thenReturn(Mono.just(setting.getSpec().getDefaults()));
        when(client.get(AiAgent.class, "agent-a"))
                .thenReturn(Mono.just(agent("agent-a", "team-a", "alice", AiAgent.Visibility.TEAM)));

        StepVerifier.create(service.defaultAgent())
                .assertNext(found -> assertThat(found.getMetadata().getName()).isEqualTo("agent-a"))
                .verifyComplete();
    }


    @Test
    void addMemberCreatesAnActiveMember() {
        var team = team("team-a", "alice");
        when(userService.getUserByUid("bob")).thenReturn(Mono.just(new User()));
        when(client.get(AiTeam.class, "team-a")).thenReturn(Mono.just(team));
        when(client.update(team)).thenReturn(Mono.just(team));

        StepVerifier.create(service.addMember("team-a", "bob", AiTeam.TeamRole.REVIEWER))
                .assertNext(updated -> {
                    var member = updated.getSpec().getMembers().stream()
                            .filter(entry -> "bob".equals(entry.getUserId()))
                            .findFirst()
                            .orElseThrow();
                    assertThat(member.getRole()).isEqualTo(AiTeam.TeamRole.REVIEWER);
                    assertThat(member.getStatus()).isEqualTo(AiTeam.MemberStatus.ACTIVE);
                })
                .verifyComplete();
    }

    @Test
    void addMemberRejectsAnUnknownUser() {
        when(userService.getUserByUid("missing")).thenReturn(Mono.empty());

        StepVerifier.create(service.addMember("team-a", "missing", AiTeam.TeamRole.MEMBER))
                .expectError(org.springframework.web.server.ServerWebInputException.class)
                .verify();
    }

    @Test
    void removeMemberMarksTheMemberRemoved() {
        var team = team("team-a", "alice");
        var bob = new AiTeam.Member();
        bob.setUserId("bob");
        team.getSpec().getMembers().add(bob);
        when(client.get(AiTeam.class, "team-a")).thenReturn(Mono.just(team));
        when(client.update(team)).thenReturn(Mono.just(team));

        StepVerifier.create(service.removeMember("team-a", "bob"))
                .assertNext(updated -> {
                    var member = updated.getSpec().getMembers().stream()
                            .filter(entry -> "bob".equals(entry.getUserId()))
                            .findFirst()
                            .orElseThrow();
                    assertThat(member.getStatus()).isEqualTo(AiTeam.MemberStatus.REMOVED);
                })
                .verifyComplete();
    }

    private static AiTeam team(String name, String userId) {
        var team = new AiTeam();
        var metadata = new cn.tolim.pudans.contract.extension.Metadata();
        metadata.setName(name);
        team.setMetadata(metadata);
        team.getSpec().setDisplayName(name);
        team.getSpec().setOwnerUserId(userId);
        var member = new AiTeam.Member();
        member.setUserId(userId);
        member.setRole(AiTeam.TeamRole.ADMIN);
        team.getSpec().getMembers().add(member);
        return team;
    }

    private static AiAgent agent(String name, String team, String owner, AiAgent.Visibility visibility) {
        var agent = new AiAgent();
        var metadata = new cn.tolim.pudans.contract.extension.Metadata();
        metadata.setName(name);
        agent.setMetadata(metadata);
        agent.getSpec().setTeam(team);
        agent.getSpec().setOwnerUserId(owner);
        agent.getSpec().setDisplayName(name);
        agent.getSpec().setVisibility(visibility);
        return agent;
    }
}
