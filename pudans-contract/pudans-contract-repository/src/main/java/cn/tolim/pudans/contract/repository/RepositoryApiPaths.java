package cn.tolim.pudans.contract.repository;

// File repository service API paths
public final class RepositoryApiPaths {

    public static final String BASE = "/apis/pudans.tolim.cn/v1alpha1/repository";

    public static final String FILES_SUFFIX = "/files";
    public static final String FILES = BASE + FILES_SUFFIX;

    public static final String FILE_DETAIL_SUFFIX = "/files/{id}";
    public static final String FILE_DETAIL = BASE + FILE_DETAIL_SUFFIX;

    public static final String FILE_CONTENT_SUFFIX = "/files/{id}/content";
    public static final String FILE_CONTENT = BASE + FILE_CONTENT_SUFFIX;

    public static final String FILE_URL_SUFFIX = "/files/{id}/url";
    public static final String FILE_URL = BASE + FILE_URL_SUFFIX;

    public static final String PROVIDERS_SUFFIX = "/files/providers";
    public static final String PROVIDERS = BASE + PROVIDERS_SUFFIX;

    private RepositoryApiPaths() {
    }
}
