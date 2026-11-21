package cn.tolim.pudans.core.storage.driver;

import cn.tolim.pudans.infra.exception.ApiException;
import org.springframework.http.HttpStatus;

// 统一存储驱动的通用错误，避免各驱动重复定义相同错误码。
public final class StorageDriverErrors {

    public static final String NOT_FOUND_CODE = "storage-object-not-found";

    public static final String EXISTS_CODE = "storage-object-exists";

    private StorageDriverErrors() {}

    // 对象不存在时返回统一的 404 错误。
    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND_CODE, "文件不存在。");
    }

    // 目标对象已存在时返回统一的 409 错误。
    public static ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, EXISTS_CODE, "文件已存在。");
    }
}
