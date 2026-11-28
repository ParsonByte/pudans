package cn.tolim.pudans.contract.repository.dto;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Storage provider summary
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageProviderResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Storage provider code: local, aliyun_oss, tencent_cos, minio or s3
    private String provider;

    // Whether the provider is configured
    private Boolean configured;

    // Whether the provider is the default provider
    private Boolean defaultProvider;
}
