package cn.tolim.pudans.contract.search.client;

import cn.tolim.pudans.contract.search.SearchApiPaths;
import cn.tolim.pudans.contract.search.SearchContractConstants;
import cn.tolim.pudans.contract.search.dto.BatchIndexResult;
import cn.tolim.pudans.contract.search.dto.SearchApiResult;
import cn.tolim.pudans.contract.search.dto.SearchDocumentBatchRequest;
import cn.tolim.pudans.contract.search.dto.SearchDocumentDeleteResult;
import cn.tolim.pudans.contract.search.dto.SearchDocumentRequest;
import cn.tolim.pudans.contract.search.dto.SearchDocumentResponse;
import cn.tolim.pudans.contract.search.dto.SearchDocumentUpdateRequest;
import cn.tolim.pudans.contract.search.dto.SearchHitResponse;
import cn.tolim.pudans.contract.search.dto.SearchIndexCreateRequest;
import cn.tolim.pudans.contract.search.dto.SearchIndexDeleteResult;
import cn.tolim.pudans.contract.search.dto.SearchIndexResponse;
import cn.tolim.pudans.contract.search.dto.SearchIndexUpdateRequest;
import cn.tolim.pudans.contract.search.dto.SearchPageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

// Search service contract
@FeignClient(name = SearchContractConstants.SERVICE_NAME, contextId = "searchClient",
        path = SearchApiPaths.BASE)
public interface SearchClient {

    // Register a new search index
    // Parameter request: SearchIndexCreateRequest
    // Returns: SearchApiResult<SearchIndexResponse>
    @PostMapping(SearchApiPaths.INDEXES_SUFFIX)
    SearchApiResult<SearchIndexResponse> createIndex(@RequestBody SearchIndexCreateRequest request);

    // List every index of one application
    // Parameter appCode: String
    // Returns: SearchApiResult<List<SearchIndexResponse>>
    @GetMapping(SearchApiPaths.INDEXES_SUFFIX)
    SearchApiResult<List<SearchIndexResponse>> listIndexes(@RequestParam("appCode") String appCode);

    // Load one index
    // Parameter appCode: String
    // Parameter indexCode: String
    // Returns: SearchApiResult<SearchIndexResponse>
    @GetMapping(SearchApiPaths.INDEX_DETAIL_SUFFIX)
    SearchApiResult<SearchIndexResponse> getIndex(@RequestParam("appCode") String appCode,
            @PathVariable("indexCode") String indexCode);

    // Update index metadata
    // Parameter appCode: String
    // Parameter indexCode: String
    // Parameter request: SearchIndexUpdateRequest
    // Returns: SearchApiResult<SearchIndexResponse>
    @PutMapping(SearchApiPaths.INDEX_DETAIL_SUFFIX)
    SearchApiResult<SearchIndexResponse> updateIndex(@RequestParam("appCode") String appCode,
            @PathVariable("indexCode") String indexCode,
            @RequestBody SearchIndexUpdateRequest request);

    // Delete one index together with its documents
    // Parameter appCode: String
    // Parameter indexCode: String
    // Returns: SearchApiResult<SearchIndexDeleteResult>
    @DeleteMapping(SearchApiPaths.INDEX_DETAIL_SUFFIX)
    SearchApiResult<SearchIndexDeleteResult> deleteIndex(
            @RequestParam("appCode") String appCode, @PathVariable("indexCode") String indexCode);

    // Insert or replace one document
    // Parameter appCode: String
    // Parameter indexCode: String
    // Parameter request: SearchDocumentRequest
    // Returns: SearchApiResult<SearchDocumentResponse>
    @PostMapping(SearchApiPaths.INDEX_DOCUMENTS_SUFFIX)
    SearchApiResult<SearchDocumentResponse> indexDocument(
            @RequestParam("appCode") String appCode, @PathVariable("indexCode") String indexCode,
            @RequestBody SearchDocumentRequest request);

    // Insert or replace a batch of documents
    // Parameter appCode: String
    // Parameter indexCode: String
    // Parameter request: SearchDocumentBatchRequest
    // Returns: SearchApiResult<BatchIndexResult>
    @PostMapping(SearchApiPaths.DOCUMENTS_BATCH_SUFFIX)
    SearchApiResult<BatchIndexResult> indexDocuments(
            @RequestParam("appCode") String appCode, @PathVariable("indexCode") String indexCode,
            @RequestBody SearchDocumentBatchRequest request);

    // Update an existing document
    // Parameter appCode: String
    // Parameter indexCode: String
    // Parameter documentId: String
    // Parameter request: SearchDocumentUpdateRequest
    // Returns: SearchApiResult<SearchDocumentResponse>
    @PutMapping(SearchApiPaths.DOCUMENT_DETAIL_SUFFIX)
    SearchApiResult<SearchDocumentResponse> updateDocument(
            @RequestParam("appCode") String appCode, @PathVariable("indexCode") String indexCode,
            @PathVariable("documentId") String documentId,
            @RequestBody SearchDocumentUpdateRequest request);

    // Load one document
    // Parameter appCode: String
    // Parameter indexCode: String
    // Parameter documentId: String
    // Returns: SearchApiResult<SearchDocumentResponse>
    @GetMapping(SearchApiPaths.DOCUMENT_DETAIL_SUFFIX)
    SearchApiResult<SearchDocumentResponse> getDocument(
            @RequestParam("appCode") String appCode, @PathVariable("indexCode") String indexCode,
            @PathVariable("documentId") String documentId);

    // Delete one document
    // Parameter appCode: String
    // Parameter indexCode: String
    // Parameter documentId: String
    // Returns: SearchApiResult<SearchDocumentDeleteResult>
    @DeleteMapping(SearchApiPaths.DOCUMENT_DETAIL_SUFFIX)
    SearchApiResult<SearchDocumentDeleteResult> deleteDocument(
            @RequestParam("appCode") String appCode, @PathVariable("indexCode") String indexCode,
            @PathVariable("documentId") String documentId);

    // Search indexed documents with pagination
    // Parameter appCode: String
    // Parameter indexCode: String
    // Parameter keyword: String
    // Parameter ownerId: String
    // Parameter orgId: String
    // Parameter page: Integer
    // Parameter size: Integer
    // Parameter sort: String
    // Returns: SearchApiResult<SearchPageResponse<SearchHitResponse>>
    @GetMapping(SearchApiPaths.QUERY_SUFFIX)
    SearchApiResult<SearchPageResponse<SearchHitResponse>> search(
            @RequestParam("appCode") String appCode,
            @RequestParam(value = "indexCode", required = false) String indexCode,
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "ownerId", required = false) String ownerId,
            @RequestParam(value = "orgId", required = false) String orgId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sort", required = false) String sort);
}