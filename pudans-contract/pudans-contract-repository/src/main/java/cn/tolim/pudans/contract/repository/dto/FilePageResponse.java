package cn.tolim.pudans.contract.repository.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// File page result
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FilePageResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Files on the current page
    private List<FileMetadataResponse> items;

    // Total number of files
    private Long total;

    // Page number starting at zero
    private Integer page;

    // Page size
    private Integer size;

    // Total number of pages
    private Integer totalPages;
}
