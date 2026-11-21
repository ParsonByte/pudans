package cn.tolim.pudans.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.properties.PudansProperties;

class TransferTokensTest {

    @TempDir
    Path workDir;

    private TransferTokens service;

    @BeforeEach
    void setUp() {
        var properties = new PudansProperties();
        properties.setWorkDir(workDir);
        service = new TransferTokens(properties);
    }

    @Test
    void acceptsAValidDownloadToken() {
        var token = service.signDownload("file-1", Duration.ofMinutes(5));

        service.verifyDownload(token, "file-1");
    }

    @Test
    void rejectsATamperedToken() {
        var token = service.signDownload("file-1", Duration.ofMinutes(5));

        assertThatThrownBy(() -> service.verifyDownload(token + "x", "file-1"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("storage-token-invalid"));
    }

    @Test
    void rejectsAnotherFileId() {
        var token = service.signDownload("file-1", Duration.ofMinutes(5));

        assertThatThrownBy(() -> service.verifyDownload(token, "file-2"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        var token = service.signDownload("file-1", Duration.ofSeconds(-1));

        assertThatThrownBy(() -> service.verifyDownload(token, "file-1"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void separatesUploadAndDownloadTokens() {
        var token = service.signDownload("file-1", Duration.ofMinutes(5));

        assertThatThrownBy(() -> service.verifyUpload(token, "file-1"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void signsAndVerifiesOAuthState() {
        var state = service.signOAuthState("onedrive-policy", Duration.ofMinutes(5));

        assertThat(service.verifyOAuthState(state)).isEqualTo("onedrive-policy");
    }

    @Test
    void rejectsANonOAuthTokenAsOAuthState() {
        var token = service.signDownload("file-1", Duration.ofMinutes(5));

        assertThatThrownBy(() -> service.verifyOAuthState(token)).isInstanceOf(ApiException.class);
    }
}
