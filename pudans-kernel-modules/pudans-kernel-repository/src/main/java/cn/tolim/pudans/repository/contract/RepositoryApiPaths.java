package cn.tolim.pudans.repository.contract;

public final class RepositoryApiPaths {

    public static final String API_PREFIX = "/apis/pudans.tolim.cn/v1alpha1/repository";
    public static final String FILES = API_PREFIX + "/files";

    private RepositoryApiPaths() {
    }

    // Route that streams file content through the repository service
    public static String fileContentPath(String fileId) {
        return FILES + "/" + fileId + "/content";
    }
}