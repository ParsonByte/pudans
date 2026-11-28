package cn.tolim.pudans.iam.core.storage;

import cn.tolim.pudans.contract.repository.client.FileRepositoryClient;
import cn.tolim.pudans.contract.repository.dto.FileMetadataResponse;
import cn.tolim.pudans.contract.repository.dto.RepositoryApiResult;
import cn.tolim.pudans.iam.communal.storage.Storage;
import cn.tolim.pudans.iam.communal.storage.StorageContent;
import cn.tolim.pudans.iam.communal.storage.controller.StorageFileEndpoint;
import feign.Response;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

// IAM storage adapter backed by the platform file repository service
public class RepositoryStorage implements Storage {

    private static final String APP_CODE = "iam";
    private static final String CATEGORY = "iam-storage";
    private static final String CREATED_BY = "pudans-iam";
    private static final String SUCCESS_CODE = "OK";

    private final FileRepositoryClient repositoryClient;

    public RepositoryStorage(FileRepositoryClient repositoryClient) {
        this.repositoryClient = repositoryClient;
    }

    @Override
    public String upload(String fileName, InputStream inputStream) throws Exception {
        byte[] content = inputStream.readAllBytes();
        InMemoryMultipartFile file = new InMemoryMultipartFile(fileName,
                contentType(fileName), content);
        RepositoryApiResult<FileMetadataResponse> result = repositoryClient.uploadFile(file, null,
                APP_CODE, CATEGORY, null, CREATED_BY);
        if (result == null || !SUCCESS_CODE.equals(result.getCode()) || result.getData() == null) {
            throw new IOException("Repository upload failed: "
                    + (result == null ? "empty response" : result.getMessage()));
        }
        return contentUrl(result.getData().getId());
    }

    @Override
    public String download(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("/")) {
            return path;
        }
        return contentUrl(path);
    }

    @Override
    public StorageContent open(String path) throws Exception {
        Response response = repositoryClient.downloadContent(path, null);
        if (response == null || response.status() != 200 || response.body() == null) {
            throw new IOException("Repository content is unavailable: " + path);
        }
        return new StorageContent(response.body().asInputStream(),
                header(response, "Content-Type"),
                parseLong(header(response, "Content-Length"), -1),
                fileName(header(response, "Content-Disposition")));
    }

    private static String contentUrl(String fileId) {
        return StorageFileEndpoint.STORAGE_PATH + "/content?path="
                + URLEncoder.encode(fileId, StandardCharsets.UTF_8);
    }

    private static String header(Response response, String name) {
        Collection<String> values = response.headers().get(name);
        if (values == null) {
            for (Map.Entry<String, Collection<String>> entry : response.headers().entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    values = entry.getValue();
                    break;
                }
            }
        }
        return values == null || values.isEmpty() ? null : values.iterator().next();
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String fileName(String contentDisposition) {
        if (contentDisposition == null) {
            return null;
        }
        int index = contentDisposition.toLowerCase(Locale.ROOT).indexOf("filename=");
        if (index < 0) {
            return null;
        }
        String name = contentDisposition.substring(index + "filename=".length()).trim();
        if (name.startsWith("\"") && name.endsWith("\"") && name.length() > 1) {
            name = name.substring(1, name.length() - 1);
        }
        return name.isBlank() ? null : name;
    }

    private static String contentType(String fileName) {
        if (fileName == null) {
            return "application/octet-stream";
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".ico")) {
            return "image/vnd.microsoft.icon";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    // Multipart part backed by an already buffered file payload
    private static final class InMemoryMultipartFile implements MultipartFile {

        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        private InMemoryMultipartFile(String originalFilename, String contentType, byte[] content) {
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), content);
        }
    }
}