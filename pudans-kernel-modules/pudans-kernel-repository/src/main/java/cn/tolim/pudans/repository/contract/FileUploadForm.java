package cn.tolim.pudans.repository.contract;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class FileUploadForm {

    private MultipartFile file;
    private String tenantId;
    private String appCode;
    private String category;
    private String provider;
    private String createdBy;
    private Map<String, String> metadata = new LinkedHashMap<>();
}