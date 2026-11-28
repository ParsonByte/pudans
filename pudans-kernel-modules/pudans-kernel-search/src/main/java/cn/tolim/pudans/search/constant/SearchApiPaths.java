package cn.tolim.pudans.search.constant;

public interface SearchApiPaths {

    // Base path of the search service API
    String API_PREFIX = "/apis/pudans.tolim.cn/v1alpha1/search";

    // Index management resources
    String INDEXES = API_PREFIX + "/indexes";

    // Full-text query endpoint
    String QUERY = API_PREFIX + "/query";
}