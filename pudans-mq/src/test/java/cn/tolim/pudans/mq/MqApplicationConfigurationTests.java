package cn.tolim.pudans.mq;

import cn.tolim.pudans.mq.endpoint.MessageCenterEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MqApplicationConfigurationTests {

    @Test
    void applicationYmlDeclaresServiceRuntimeConfiguration() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        assertThat(sources).isNotEmpty();
        PropertySource<?> source = sources.getFirst();

        assertThat(String.valueOf(source.getProperty("server.port"))).isEqualTo("8093");
        assertThat(source.getProperty("spring.application.name")).isEqualTo("pudans-mq");
        assertThat(String.valueOf(source.getProperty("spring.datasource.url")))
                .contains("jdbc:postgresql://");
        assertThat(String.valueOf(source.getProperty("spring.datasource.username")))
                .isNotBlank();
        assertThat(source.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(source.getProperty("spring.liquibase.enabled")).isEqualTo(true);
        assertThat(source.getProperty("spring.liquibase.change-log"))
                .isEqualTo("classpath:mq/changelog-master.xml");
        assertThat(String.valueOf(source.getProperty("spring.config.import")))
                .contains("nacos:")
                .contains("refreshEnabled=true");
    }

    @Test
    void nacosSeedDeclaresBusinessConfiguration() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("pudans-mq", new ClassPathResource("pudans-mq.yaml"));
        assertThat(sources).isNotEmpty();
        PropertySource<?> source = sources.getFirst();

        assertThat(String.valueOf(source.getProperty("pudans.internal.token"))).isNotBlank();
        assertThat(source.getProperty("pudans.mq.scheduler-enabled")).isEqualTo(true);
    }

    @Test
    void masterChangelogIncludesKernelMqChangelog() throws Exception {
        Resource master = new ClassPathResource("mq/changelog-master.xml");
        assertThat(master.exists()).isTrue();
        Document document = parse(master);
        assertThat(document.getDocumentElement().getLocalName()).isEqualTo("databaseChangeLog");

        List<String> includes = new ArrayList<>();
        NodeList includeNodes = document.getElementsByTagNameNS("*", "include");
        for (int index = 0; index < includeNodes.getLength(); index++) {
            includes.add(((Element) includeNodes.item(index)).getAttribute("file"));
        }
        assertThat(includes).contains("classpath:mq/1.0.0-mq-changelog.xml");

        Resource kernelChangelog = new ClassPathResource("mq/1.0.0-mq-changelog.xml");
        assertThat(kernelChangelog.exists()).isTrue();
        Document kernelDocument = parse(kernelChangelog);
        NodeList tables = kernelDocument.getElementsByTagNameNS("*", "createTable");
        List<String> tableNames = new ArrayList<>();
        for (int index = 0; index < tables.getLength(); index++) {
            tableNames.add(((Element) tables.item(index)).getAttribute("tableName"));
        }
        assertThat(tableNames).containsExactlyInAnyOrder(
                "pudans_mq_message", "pudans_mq_delivery", "pudans_mq_dead_letter");
    }

    @Test
    void applicationComponentScanDiscoversMessageCenterEndpoint() {
        SpringBootApplication annotation =
                MqApplication.class.getAnnotation(SpringBootApplication.class);
        assertThat(annotation).isNotNull();
        Set<String> scanPackages = Set.of(annotation.scanBasePackages());
        assertThat(scanPackages).contains("cn.tolim.pudans.mq");

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> controllers = new ArrayList<>();
        for (String scanPackage : scanPackages) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(scanPackage)) {
                controllers.add(candidate.getBeanClassName());
            }
        }
        assertThat(controllers).contains(MessageCenterEndpoint.class.getName());
    }

    private static Document parse(Resource resource) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream inputStream = resource.getInputStream()) {
            return builder.parse(inputStream);
        }
    }
}