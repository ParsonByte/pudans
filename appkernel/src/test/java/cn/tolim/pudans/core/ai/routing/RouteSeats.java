package cn.tolim.pudans.core.ai.routing;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;

public final class RouteSeats {

    private RouteSeats() {
    }

    public static RouteSeat seat(String provider, String account, String model, AiModel.Tier tier) {
        return RouteSeat.builder()
                .provider(provider)
                .account(account)
                .model(model)
                .tier(tier)
                .build();
    }
}
