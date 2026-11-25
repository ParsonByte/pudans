package cn.tolim.pudans.contract.creation.dto;

import cn.tolim.pudans.contract.creation.Asset;
import java.util.List;

public record AssetView(Asset asset, List<Asset> ancestors) {}
