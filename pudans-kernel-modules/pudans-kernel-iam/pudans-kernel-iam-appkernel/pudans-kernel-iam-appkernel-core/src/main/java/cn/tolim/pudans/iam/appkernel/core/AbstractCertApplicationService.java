package cn.tolim.pudans.iam.appkernel.core;

import cn.tolim.pudans.iam.communal.entity.app.AppCertEntity;
import cn.tolim.pudans.iam.communal.enums.app.AppCertUsingType;
import cn.tolim.pudans.iam.communal.exception.app.AppCreateCertException;
import cn.tolim.pudans.iam.communal.repository.app.*;
import cn.tolim.pudans.iam.infra.util.CertUtils;
import cn.tolim.pudans.iam.infra.util.RsaUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.asn1.x500.X500Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.IdGenerator;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static cn.tolim.pudans.iam.infra.util.CertUtils.encodePem;
import static cn.tolim.pudans.iam.infra.util.CertUtils.getX500Name;
import static cn.tolim.pudans.iam.infra.util.RsaUtils.getKeys;


public abstract class AbstractCertApplicationService extends AbstractApplicationService {
    protected final ObjectMapper mapper = new ObjectMapper();
    // AppCertRepository
    protected final AppCertRepository appCertRepository;
    // AppAccessPolicyRepository
    protected final AppAccessPolicyRepository appAccessPolicyRepository;
    // IdGenerator
    protected final IdGenerator idGenerator;
    private final Logger logger = LoggerFactory
            .getLogger(AbstractCertApplicationService.class);

    protected AbstractCertApplicationService(AppCertRepository appCertRepository,
                                             AppGroupAssociationRepository appGroupAssociationRepository,
                                             AppAccountRepository appAccountRepository,
                                             AppAccessPolicyRepository appAccessPolicyRepository,
                                             AppRepository appRepository) {
        super(appAccountRepository, appGroupAssociationRepository, appRepository);
        this.appCertRepository = appCertRepository;
        this.appAccessPolicyRepository = appAccessPolicyRepository;
        this.idGenerator = new AlternativeJdkIdGenerator();
    }

    // Create certificate
    public void createCertificate(String appId, String appCode, AppCertUsingType usingType) {
        try {
            AppCertEntity config = new AppCertEntity();
            config.setAppId(appId);
            // Private key length
            config.setKeyLong(2048);
            // Algorithm
            config.setSignAlgo("SHA256WITHRSA");
            RsaUtils.RsaResult keys = getKeys(config.getKeyLong());
            X500Name x500Name = getX500Name("app_" + appCode, "PUDANS", "Jinan", "Shandong", "CN",
                    "IAM");
            // Issuer
            config.setIssuer(x500Name.toString());
            // Subject
            config.setSubject(x500Name.toString());
            // Certificate start and end dates
            LocalDateTime localDateTime = LocalDateTime.now();
            // Certificate serial number
            config.setSerial(BigInteger.valueOf(System.currentTimeMillis()));
            // Start time
            Date notBefore = Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
            config.setBeginDate(
                    notBefore.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
            // End time
            Date notAfter = Date
                    .from(localDateTime.plusYears(10).atZone(ZoneId.systemDefault()).toInstant());
            config
                    .setEndDate(notAfter.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
            // Difference in days
            config
                    .setValidity((int) ((notAfter.getTime() - notBefore.getTime()) / 1000 / 3600 / 24));
            // Generate certificate
            String certificate = CertUtils.getCertificate(x500Name, x500Name, config.getSerial(),
                    notBefore, notAfter, keys.getPublicKey(), keys.getPrivateKey());
            // Private key
            config.setPrivateKey(encodePem(keys.getPrivateKey()));
            // Public key
            config.setPublicKey(encodePem(keys.getPublicKey()));
            // Certificate
            config.setCert(certificate);
            // Usage type
            config.setUsingType(usingType);
            appCertRepository.save(config);
        } catch (Exception e) {
            logger.error("创建应用证书异常", e);
            throw new AppCreateCertException();
        }
    }
}
