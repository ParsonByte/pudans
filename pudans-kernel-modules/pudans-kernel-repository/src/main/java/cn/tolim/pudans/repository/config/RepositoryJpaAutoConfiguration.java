package cn.tolim.pudans.repository.config;

import cn.tolim.pudans.repository.contract.FileEndpoint;
import cn.tolim.pudans.repository.repository.RepositoryFileRepository;
import cn.tolim.pudans.repository.service.ChecksumService;
import cn.tolim.pudans.repository.service.FileStorageService;
import cn.tolim.pudans.repository.storage.ObjectStorageRegistry;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.sql.DataSource;

// Run after Boot's repository auto-configuration so the host application keeps its own scanning
@AutoConfiguration(afterName = {
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
})
@ConditionalOnClass({JpaRepository.class, EntityManagerFactory.class})
@ConditionalOnSingleCandidate(DataSource.class)
@EntityScan(basePackages = "cn.tolim.pudans.repository.entity")
@EnableJpaRepositories(basePackages = "cn.tolim.pudans.repository.repository")
public class RepositoryJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FileStorageService.class)
    public FileStorageService fileStorageService(RepositoryFileRepository repositoryFileRepository,
                                                 ObjectStorageRegistry objectStorageRegistry,
                                                 ChecksumService checksumService,
                                                 RepositoryProperties properties) {
        return new FileStorageService(repositoryFileRepository, objectStorageRegistry,
                checksumService, properties);
    }

    @Bean
    @ConditionalOnMissingBean(FileEndpoint.class)
    public FileEndpoint fileEndpoint(FileStorageService fileStorageService,
                                     ObjectStorageRegistry objectStorageRegistry) {
        return new FileEndpoint(fileStorageService, objectStorageRegistry);
    }
}