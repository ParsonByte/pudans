package cn.tolim.pudans.repository.service;

import cn.tolim.pudans.repository.config.RepositoryProperties;
import cn.tolim.pudans.repository.error.RepositoryErrorCode;
import cn.tolim.pudans.repository.error.RepositoryException;
import cn.tolim.spissatus4j.contract.algorithm.AlgorithmId;
import cn.tolim.spissatus4j.contract.crypto.DigestRequest;
import cn.tolim.spissatus4j.contract.api.Spissatus;
import cn.tolim.spissatus4j.contract.policy.CallerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

// SHA-256 checksum computation performed through the spissatus4j facade
public final class ChecksumService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChecksumService.class);

    private final Spissatus spissatus;
    private final RepositoryProperties.Crypto cryptoProperties;
    private final boolean enabled;
    private final long maxBytes;

    public ChecksumService(Spissatus spissatus, RepositoryProperties properties) {
        this.spissatus = spissatus;
        this.cryptoProperties = properties.getCrypto();
        this.enabled = properties.getChecksum().isEnabled();
        this.maxBytes = properties.getChecksum().getMaxBytes();
    }

    public String sha256(ContentSource source, long size) {
        if (!enabled || spissatus == null || source == null || size < 0 || size > maxBytes) {
            return null;
        }
        int capacity = (int) Math.min(Math.min(size, maxBytes), Integer.MAX_VALUE - 8);
        try (InputStream input = source.open();
             ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(capacity, 32))) {
            input.transferTo(output);
            CallerContext caller = new CallerContext(cryptoProperties.getCallerId(),
                    cryptoProperties.getTenantId(), Set.of("system"), cryptoProperties
                    .getCallerEnvironment(), Map.of());
            byte[] digest = spissatus.crypto()
                    .digest(new DigestRequest(AlgorithmId.SHA_256, output.toByteArray(), caller));
            return HexFormat.of().formatHex(digest);
        } catch (IOException exception) {
            throw new RepositoryException(RepositoryErrorCode.INVALID_REQUEST,
                    "Upload content could not be read for checksum computation", exception);
        } catch (RuntimeException exception) {
            LOGGER.warn("Checksum computation skipped", exception);
            return null;
        }
    }
}