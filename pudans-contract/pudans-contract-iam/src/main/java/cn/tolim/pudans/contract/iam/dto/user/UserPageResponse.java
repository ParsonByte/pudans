package cn.tolim.pudans.contract.iam.dto.user;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// User page result
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserPageResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Users on the current page
    private List<UserSummary> items;

    // Total number of users
    private Long total;

    // Page number starting at zero
    private Integer page;

    // Page size
    private Integer size;

    // Total number of pages
    private Integer pages;
}
