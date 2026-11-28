package cn.tolim.pudans.search.controller;

import cn.tolim.pudans.communal.core.contract.ApiResponse;
import cn.tolim.pudans.search.constant.SearchApiPaths;
import cn.tolim.pudans.search.model.BatchIndexResult;
import cn.tolim.pudans.search.model.SearchDocumentBatchRequest;
import cn.tolim.pudans.search.model.SearchDocumentDeleteResult;
import cn.tolim.pudans.search.model.SearchDocumentRequest;
import cn.tolim.pudans.search.model.SearchDocumentResponse;
import cn.tolim.pudans.search.model.SearchDocumentUpdateRequest;
import cn.tolim.pudans.search.service.SearchDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "搜索文档")
@RequestMapping(value = SearchApiPaths.INDEXES + "/{indexCode}/documents",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class SearchDocumentEndpoint {

    private final SearchDocumentService documentService;

    public SearchDocumentEndpoint(SearchDocumentService documentService) {
        this.documentService = documentService;
    }

    // Insert or replace one document
    @Operation(summary = "创建文档")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<SearchDocumentResponse> indexDocument(
            @RequestParam("appCode") String appCode,
            @PathVariable("indexCode") String indexCode,
            @Valid @RequestBody SearchDocumentRequest request) {
        return ApiResponse.ok(documentService.indexDocument(appCode, indexCode, request));
    }

    // Insert or replace a batch of documents in one transaction
    @Operation(summary = "批量创建文档")
    @PostMapping(value = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<BatchIndexResult> indexDocuments(
            @RequestParam("appCode") String appCode,
            @PathVariable("indexCode") String indexCode,
            @Valid @RequestBody SearchDocumentBatchRequest request) {
        return ApiResponse.ok(documentService.indexDocuments(appCode, indexCode, request));
    }

    // Update an existing document
    @Operation(summary = "更新文档")
    @PutMapping(value = "/{documentId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<SearchDocumentResponse> updateDocument(
            @RequestParam("appCode") String appCode,
            @PathVariable("indexCode") String indexCode,
            @PathVariable("documentId") String documentId,
            @Valid @RequestBody SearchDocumentUpdateRequest request) {
        return ApiResponse.ok(documentService.updateDocument(appCode, indexCode, documentId, request));
    }

    // Load one document with its full content
    @Operation(summary = "查询文档详情")
    @GetMapping("/{documentId}")
    public ApiResponse<SearchDocumentResponse> getDocument(
            @RequestParam("appCode") String appCode,
            @PathVariable("indexCode") String indexCode,
            @PathVariable("documentId") String documentId) {
        return ApiResponse.ok(documentService.getDocument(appCode, indexCode, documentId));
    }

    // Delete one document
    @Operation(summary = "删除文档")
    @DeleteMapping("/{documentId}")
    public ApiResponse<SearchDocumentDeleteResult> deleteDocument(
            @RequestParam("appCode") String appCode,
            @PathVariable("indexCode") String indexCode,
            @PathVariable("documentId") String documentId) {
        return ApiResponse.ok(documentService.deleteDocument(appCode, indexCode, documentId));
    }
}