package cn.tolim.pudans.contract.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

// Spring Data page result exposed by the notification API
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationPageResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Items on the current page
    private List<T> content;

    // Total number of items
    private Long totalElements;

    // Total number of pages
    private Integer totalPages;

    // Zero-based page number
    private Integer number;

    // Page size
    private Integer size;

    // Whether this is the first page
    private Boolean first;

    // Whether this is the last page
    private Boolean last;

    // Number of items on this page
    private Integer numberOfElements;

    // Whether this page is empty
    private Boolean empty;
}
