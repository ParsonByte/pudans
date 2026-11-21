package cn.tolim.pudans.security.contract;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import org.junit.jupiter.api.Test;

class PersonalAccessTokenTest {

    @Test
    void readsTheLegacyPointsAliasIntoUnits() {
        var token = JsonUtils.jsonToObject(
                "{\"spec\":{\"gateway\":{\"limits\":[{\"points\":120}]}}}",
                PersonalAccessToken.class);

        assertThat(token.getSpec().getGateway().getLimits().getFirst().getUnits())
                .isEqualTo(120L);
    }

    @Test
    void writesTheUnitBudgetUnderTheUnitsField() {
        var token = new PersonalAccessToken();
        var limit = new PersonalAccessToken.Limit();
        limit.setUnits(120L);
        token.getSpec().getGateway().getLimits().add(limit);

        assertThat(JsonUtils.objectToJson(token))
                .contains("\"units\":120")
                .doesNotContain("\"points\"");
    }
}