package cn.tolim.pudans.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Guards the repository service master changelog and the resources it includes
class RepositoryChangelogTests {

    private static final String MASTER = "repository/changelog-master.xml";

    @Test
    void masterChangelogIncludesKernelAndServiceChanges() throws Exception {
        Document master = parse(MASTER);
        List<String> includes = includes(master);

        assertThat(includes).contains(
                "classpath:repository/1.0.0-repository-changelog.xml",
                "classpath:repository/1.1.0-repository-changelog.xml");
        for (String include : includes) {
            String location = include.substring("classpath:".length());
            assertThat(new ClassPathResource(location).exists())
                    .as("included changelog %s", include)
                    .isTrue();
        }
    }

    @Test
    void kernelChangelogCreatesRepositoryTable() throws Exception {
        String xml = read("repository/1.0.0-repository-changelog.xml");

        assertThat(xml).contains("tableName=\"pudans_repository_file\"")
                .contains("name=\"metadata_\"")
                .contains("type=\"jsonb\"")
                .contains("indexName=\"idx_repository_file_tenant_app\"");
    }

    @Test
    void serviceChangelogAddsListingIndex() throws Exception {
        String xml = read("repository/1.1.0-repository-changelog.xml");

        assertThat(xml).contains("indexName=\"idx_repository_file_tenant_created\"")
                .contains("column name=\"create_time\"");
    }

    private static Document parse(String location) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setNamespaceAware(true);
        try (InputStream input = new ClassPathResource(location).getInputStream()) {
            return factory.newDocumentBuilder().parse(input);
        }
    }

    private static String read(String location) throws Exception {
        return new ClassPathResource(location).getContentAsString(StandardCharsets.UTF_8);
    }

    private static List<String> includes(Document document) {
        List<String> includes = new ArrayList<>();
        NodeList nodes = document.getElementsByTagNameNS("*", "include");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            includes.add(element.getAttribute("file"));
        }
        return includes;
    }

}