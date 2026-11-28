package cn.tolim.pudans.repository.storage.s3;

import cn.tolim.pudans.repository.config.RepositoryProperties;
import cn.tolim.pudans.repository.error.RepositoryErrorCode;
import cn.tolim.pudans.repository.storage.ObjectStorage;
import cn.tolim.pudans.repository.storage.StorageException;
import cn.tolim.pudans.repository.storage.StorageObjectNotFoundException;
import cn.tolim.pudans.repository.storage.StorageObjectStat;
import cn.tolim.pudans.repository.storage.StorageProviderType;
import cn.tolim.pudans.repository.storage.StoredObject;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

public final class S3ObjectStorage implements ObjectStorage {

    private static final String PROVIDER = "S3";

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3ObjectStorage(RepositoryProperties.S3 properties, String secretAccessKey) {
        require(properties.getBucket(), "bucket");
        require(properties.getAccessKeyId(), "accessKeyId");
        require(secretAccessKey, "secretAccessKey");
        this.bucket = properties.getBucket();
        StaticCredentialsProvider credentials = StaticCredentialsProvider
                .create(AwsBasicCredentials.create(properties.getAccessKeyId(), secretAccessKey));
        Region region = Region.of(properties.getRegion() == null || properties.getRegion().isBlank()
                ? "us-east-1" : properties.getRegion());
        S3ClientBuilder clientBuilder = S3Client.builder().region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(configuration -> configuration
                        .checksumValidationEnabled(false));
        S3Presigner.Builder presignerBuilder = S3Presigner.builder().region(region)
                .credentialsProvider(credentials);
        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            URI endpoint = URI.create(properties.getEndpoint());
            clientBuilder.endpointOverride(endpoint);
            presignerBuilder.endpointOverride(endpoint);
        }
        this.client = clientBuilder.build();
        this.presigner = presignerBuilder.build();
    }

    @Override
    public StorageProviderType provider() {
        return StorageProviderType.S3;
    }

    @Override
    public StoredObject put(String objectKey, InputStream content, long size, String contentType,
                            Map<String, String> metadata) {
        if (size < 0) {
            throw new StorageException(RepositoryErrorCode.INVALID_REQUEST,
                    "S3 uploads require a known content length");
        }
        try {
            PutObjectRequest.Builder request = PutObjectRequest.builder().bucket(bucket).key(objectKey);
            if (contentType != null) {
                request.contentType(contentType);
            }
            if (metadata != null && !metadata.isEmpty()) {
                request.metadata(metadata);
            }
            client.putObject(request.build(), RequestBody.fromInputStream(content, size));
            return new StoredObject(objectKey, size, null);
        } catch (S3Exception exception) {
            throw translate(objectKey, exception);
        } catch (RuntimeException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public InputStream open(String objectKey) {
        try {
            ResponseInputStream<GetObjectResponse> response = client.getObject(GetObjectRequest.builder()
                    .bucket(bucket).key(objectKey).build());
            return response;
        } catch (NoSuchKeyException exception) {
            throw new StorageObjectNotFoundException(objectKey);
        } catch (S3Exception exception) {
            throw translate(objectKey, exception);
        } catch (RuntimeException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public StorageObjectStat stat(String objectKey) {
        try {
            HeadObjectResponse response = client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket).key(objectKey).build());
            long size = response.contentLength() == null ? -1 : response.contentLength();
            return new StorageObjectStat(objectKey, size, response.contentType(), response.eTag());
        } catch (NoSuchKeyException exception) {
            throw new StorageObjectNotFoundException(objectKey);
        } catch (S3Exception exception) {
            throw translate(objectKey, exception);
        } catch (RuntimeException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (S3Exception exception) {
            throw translate(objectKey, exception);
        } catch (RuntimeException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public URI presignedGetUrl(String objectKey, Duration timeToLive) {
        try {
            GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                    .signatureDuration(timeToLive)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(objectKey).build())
                    .build();
            return URI.create(presigner.presignGetObject(request).url().toString());
        } catch (RuntimeException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public void close() {
        presigner.close();
        client.close();
    }

    private static StorageException translate(String objectKey, S3Exception exception) {
        if (exception.statusCode() == 404 || "NoSuchKey".equals(exception.awsErrorDetails() == null
                ? null : exception.awsErrorDetails().errorCode())) {
            return new StorageObjectNotFoundException(objectKey);
        }
        return new StorageException(RepositoryErrorCode.STORAGE_OPERATION_FAILED,
                PROVIDER + " operation failed for " + objectKey + ": " + exception.statusCode(),
                exception);
    }

    private static StorageException failure(String objectKey, RuntimeException exception) {
        return new StorageException(RepositoryErrorCode.STORAGE_OPERATION_FAILED,
                PROVIDER + " operation failed for " + objectKey, exception);
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new StorageException(RepositoryErrorCode.STORAGE_NOT_CONFIGURED,
                    "S3 " + name + " is required");
        }
    }
}