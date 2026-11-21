package cn.tolim.pudans.core.ai.routing;

import cn.tolim.pudans.contract.ai.AiModel;

public record RouteSelection(String provider, String account, String model, AiModel.Tier tier) {

    public static RouteSelection of(RouteSeat seat) {
        return new RouteSelection(seat.getProvider(), seat.getAccount(), seat.getModel(), seat.getTier());
    }

    public String reference() {
        return provider + "/" + model;
    }
}
