package cn.tolim.pudans.search.controller;

import cn.tolim.pudans.communal.core.contract.ApiResponse;
import cn.tolim.pudans.search.constant.SearchApiPaths;
import cn.tolim.pudans.search.model.SearchHitResponse;
import cn.tolim.pudans.search.model.SearchPageResponse;
import cn.tolim.pudans.search.model.SearchQuery;
import cn.tolim.pudans.search.service.SearchQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "搜索查询")
@RequestMapping(value = SearchApiPaths.QUERY, produces = MediaType.APPLICATION_JSON_VALUE)
public class SearchQueryEndpoint {

    private final SearchQueryService queryService;

    public SearchQueryEndpoint(SearchQueryService queryService) {
        this.queryService = queryService;
    }

    // Search indexed documents with pagination; q is optional for browse requests
    @Operation(summary = "搜索查询")
    @GetMapping
    public ApiResponse<SearchPageResponse<SearchHitResponse>> search(
            @RequestParam("appCode") String appCode,
            @RequestParam(name = "indexCode", required = false) String indexCode,
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(name = "ownerId", required = false) String ownerId,
            @RequestParam(name = "orgId", required = false) String orgId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort) {
        SearchQuery query = new SearchQuery(appCode, indexCode, keyword, ownerId, orgId, page, size, sort);
        return ApiResponse.ok(queryService.search(query));
    }
}