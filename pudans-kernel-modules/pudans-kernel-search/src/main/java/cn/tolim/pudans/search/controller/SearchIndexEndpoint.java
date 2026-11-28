package cn.tolim.pudans.search.controller;

import cn.tolim.pudans.communal.core.contract.ApiResponse;
import cn.tolim.pudans.search.constant.SearchApiPaths;
import cn.tolim.pudans.search.model.SearchIndexCreateRequest;
import cn.tolim.pudans.search.model.SearchIndexDeleteResult;
import cn.tolim.pudans.search.model.SearchIndexResponse;
import cn.tolim.pudans.search.model.SearchIndexUpdateRequest;
import cn.tolim.pudans.search.service.SearchIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "搜索索引")
@RequestMapping(value = SearchApiPaths.INDEXES, produces = MediaType.APPLICATION_JSON_VALUE)
public class SearchIndexEndpoint {

    private final SearchIndexService indexService;

    public SearchIndexEndpoint(SearchIndexService indexService) {
        this.indexService = indexService;
    }

    // Register a new search index
    @Operation(summary = "创建索引")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SearchIndexResponse> createIndex(@Valid @RequestBody SearchIndexCreateRequest request) {
        return ApiResponse.ok(indexService.createIndex(request));
    }

    // List every index of one application
    @Operation(summary = "查询索引列表")
    @GetMapping
    public ApiResponse<List<SearchIndexResponse>> listIndexes(@RequestParam("appCode") String appCode) {
        return ApiResponse.ok(indexService.listIndexes(appCode));
    }

    // Load one index with its document count
    @Operation(summary = "查询索引详情")
    @GetMapping("/{indexCode}")
    public ApiResponse<SearchIndexResponse> getIndex(@RequestParam("appCode") String appCode,
                                                     @PathVariable("indexCode") String indexCode) {
        return ApiResponse.ok(indexService.getIndex(appCode, indexCode));
    }

    // Update index metadata
    @Operation(summary = "更新索引")
    @PutMapping(value = "/{indexCode}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<SearchIndexResponse> updateIndex(@RequestParam("appCode") String appCode,
                                                        @PathVariable("indexCode") String indexCode,
                                                        @Valid @RequestBody SearchIndexUpdateRequest request) {
        return ApiResponse.ok(indexService.updateIndex(appCode, indexCode, request));
    }

    // Delete one index together with its documents
    @Operation(summary = "删除索引")
    @DeleteMapping("/{indexCode}")
    public ApiResponse<SearchIndexDeleteResult> deleteIndex(@RequestParam("appCode") String appCode,
                                                            @PathVariable("indexCode") String indexCode) {
        return ApiResponse.ok(indexService.deleteIndex(appCode, indexCode));
    }
}