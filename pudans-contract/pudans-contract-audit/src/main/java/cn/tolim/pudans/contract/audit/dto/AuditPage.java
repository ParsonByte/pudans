package cn.tolim.pudans.contract.audit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

// Audited page result with one-based page numbers
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditPage<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Items on the current page
    private List<T> items;

    // One-based page number
    private Integer page;

    // Page size
    private Integer size;

    // Total number of items
    private Long total;

    // Total number of pages
    private Integer totalPages;
}
