package cn.tolim.pudans.iam.communal.geo.maxmind;

import cn.tolim.pudans.iam.infra.constant.IamConstants;
import cn.tolim.pudans.iam.infra.geo.GeoLocation;
import cn.tolim.pudans.iam.infra.geo.GeoLocationParser;
import cn.tolim.pudans.iam.infra.geo.GeoLocationProvider;
import cn.tolim.pudans.iam.infra.util.IpUtils;
import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.*;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static cn.tolim.pudans.iam.communal.geo.District.CITY_DISTRICT;
import static cn.tolim.pudans.iam.communal.geo.District.PROVINCE_DISTRICT;


@Slf4j
@Getter
public class MaxmindGeoLocationParserImpl implements GeoLocationParser {

    public static final GeoLocationProvider MAXMIND = new GeoLocationProvider("maxmind",
            "MAXMIND");
    // Database file download URL
    public static final String DOWNLOAD_URL = "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City&license_key=%s&suffix=tar.gz";
    // SHA-256 checksum file download URL
    public static final String SHA256_URL = "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City&license_key=%s&suffix=tar.gz.sha256";
    private final DatabaseReader reader;
    private final MaxmindProviderConfig maxmindProviderConfig;
    private final RestTemplate restTemplate;
    private final Integer MAX_RETRIES = 1999999999;

    public MaxmindGeoLocationParserImpl(MaxmindProviderConfig maxmindProviderConfig,
                                        RestTemplate restTemplate) throws IOException {
        this.maxmindProviderConfig = maxmindProviderConfig;
        this.restTemplate = restTemplate;
        download();
        this.reader = new DatabaseReader.Builder(new File(IamConstants.IPADDRESS_FILE_PATH))
                .withCache(new CHMCache()).locales(List.of("zh-CN")).build();
    }

    // Parameter file: tar.gz file
    // Parameter extractPath: Directory to extract to
    public static void unTar(File file, String extractPath) throws Exception {
        // decompressing *.tar.gz files to tar
        TarArchiveInputStream fin = new TarArchiveInputStream(
                new GzipCompressorInputStream(new FileInputStream(file)));
        File extractFolder = new File(extractPath);
        TarArchiveEntry entry;
        // Extract the tar file into extractPath
        while ((entry = fin.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            File curfile = new File(extractFolder, FilenameUtils.getName(entry.getName()));
            File parent = curfile.getParentFile();
            if (!parent.exists()) {
                if (!parent.mkdirs()) {
                    throw new IOException("创建文件路径异常");
                }
            }
            // Write files to the extraction directory
            IOUtils.copy(fin, new FileOutputStream(curfile));
        }
    }

    // Check whether the file SHA-256 is correct
    // Parameter file: File
    // Parameter sha256: SHA-256 result
    public static Boolean checkFileSha256(File file, String sha256) {
        String sha256Hex;
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            sha256Hex = DigestUtils.sha256Hex(inputStream);
            if (sha256Hex.equals(sha256)) {
                return true;
            }
        } catch (IOException e) {
            log.error("SHA256检查文件完整性失败", e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    log.error("输入流关闭异常：{}", e.getMessage());
                }
            }
        }
        return false;
    }

    // Returns geolocation information for the given IP address.
    @Override
    public GeoLocation getGeoLocation(String remote) {
        if (IpUtils.isInternalIp(remote)) {
            GeoLocation geoLocation = GeoLocation.builder().build();
            geoLocation.setIp(remote);
            geoLocation.setProvider(MAXMIND);
            return geoLocation;
        }
        try {
            CityResponse cityResponse = this.reader.tryCity(InetAddress.getByName(remote))
                    .orElseThrow();
            // Get country information
            Country country = cityResponse.getCountry();
            // Province information
            Subdivision subdivision = cityResponse.getMostSpecificSubdivision();
            // City information
            City city = cityResponse.getCity();
            Location location = cityResponse.getLocation();
            if (Objects.isNull(location)) {
                return null;
            }
            // Continent information
            Continent continent = cityResponse.getContinent();
            return GeoLocation.builder()
                .ip(remote)
                .continentCode(continent.getGeoNameId().toString())
                .continentName(continent.getName())
                .countryName(country.getName())
                .countryCode(country.getGeoNameId().toString())
                .cityName(city.getName())
                .cityCode(Objects.toString(CITY_DISTRICT.get(city.getName()), String.valueOf(city.getGeoNameId())))
                .provinceName(subdivision.getName())
                .provinceCode(Objects.toString(PROVINCE_DISTRICT.get(subdivision.getName()), subdivision.getIsoCode()))
                .longitude(location.getLongitude())
                .latitude(location.getLatitude())
                .provider(MAXMIND).build();
        } catch (Exception e) {
            log.error("获取IP地理位置发生异常 IP:[{}], 异常: {}", remote, e.getMessage());
            return GeoLocation.builder().build();
        }
    }

    public void download() {
        if (checkDbFileIsUpdate()) {
            RetryPolicy<Object> retryPolicy = RetryPolicy.builder()
                    .handle(ResourceAccessException.class)
                    .withDelay(Duration.ofSeconds(1))
                    .withMaxRetries(MAX_RETRIES)
                    .onFailure(event -> log.error("下载IP库发生网络异常"))
                    .onRetry(event -> log.error("下载IP库发生网络异常, 开始第: {} 次重试",event.getExecutionCount()))
                    .build();
            ResponseEntity<byte[]> bytes = Failsafe.with(retryPolicy).get(() -> restTemplate.exchange(String.format(DOWNLOAD_URL, maxmindProviderConfig.getSessionKey()), HttpMethod.GET, null, byte[].class));
            File path = new File(IamConstants.IPADDRESS_FILE_DIRECTORY);
            try {
                if (!path.exists()) {
                    if (!path.mkdirs()) {
                        throw new IOException("创建文件路径失败");
                    }
                }
                File file = new File(IamConstants.IPADDRESS_FILE_TAR);
                if (!file.exists()) {
                    if (!file.createNewFile()) {
                        throw new IOException("创建IP库文件失败");
                    }
                }
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(Objects.requireNonNull(bytes.getBody()), 0, bytes.getBody().length);
                    out.flush();
                } catch (Exception e) {
                    log.error("IP库文件写入异常: {}", e.getMessage());
                }
                unTar(file, IamConstants.IPADDRESS_FILE_DIRECTORY);
            } catch (Exception e) {
                log.error("下载IP库发生异常: {}", e.getMessage());
            }
        } else {
            log.debug("IP地理库无需更新");
        }
    }

    // Checks whether the local database needs an update.
    public Boolean checkDbFileIsUpdate() {
        RetryPolicy<Object> retryPolicy = RetryPolicy.builder()
                .handle(ResourceAccessException.class)
                .withDelay(Duration.ofSeconds(1))
                .withMaxRetries(MAX_RETRIES)
                .onFailure(event -> log.error("检查IP库更新失败"))
                .onRetry(event -> log.error("检查IP库更新发生网络异常, 开始第: {} 次重试",event.getExecutionCount()))
                .build();
        return Failsafe.with(retryPolicy).get(() -> {
            File ipDb = new File(IamConstants.IPADDRESS_FILE_TAR);
            if (ipDb.exists()) {
                try {
                    ResponseEntity<byte[]> sha256FileByte = restTemplate.exchange(
                            String.format(SHA256_URL,
                                    this.maxmindProviderConfig.getSessionKey()),
                            HttpMethod.GET, null, byte[].class);
                    File sha256File = new File(IamConstants.SHA256_FILE_PATH);
                    FileUtils.writeByteArrayToFile(sha256File,
                            Objects.requireNonNull(sha256FileByte.getBody()));
                    String sha256 = FileUtils.readFileToString(sha256File, StandardCharsets.UTF_8);
                    return !checkFileSha256(ipDb, sha256.split(" ")[0]);
                }
                catch (Exception e) {
                    log.error("检查MAXMIND更新异常: [{}]", e.getMessage(), e);
                    return false;
                }
            }
            return true;
        });
    }
}
