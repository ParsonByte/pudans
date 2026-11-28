package cn.tolim.pudans.search;

import cn.tolim.pudans.search.entity.SearchDocumentEntity;
import cn.tolim.pudans.search.entity.SearchIndexEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// Keeps the PostgreSQL changelog in sync with the JPA search entities without a database
class SearchChangelogTests {

    private static final String MASTER_CHANGELOG = "search/changelog-master.xml";
    private static final String SERVICE_CHANGELOG = "search/1.0.0-search-changelog.xml";

    @Test
    void masterChangelogIncludesServiceChangelog() {
        Document master = parse(MASTER_CHANGELOG);

        List<String> includes = elements(master, "include").stream()
                .map(element -> element.getAttribute("file"))
                .toList();

        assertThat(includes).contains("classpath:" + SERVICE_CHANGELOG);
    }

    @Test
    void indexTableMatchesEntity() {
        assertTableMatchesEntity("pudans_search_index", SearchIndexEntity.class);
    }

    @Test
    void documentTableMatchesEntity() {
        assertTableMatchesEntity("pudans_search_document", SearchDocumentEntity.class);
    }

    @Test
    void declaresFullTextIndexForKeywordSearch() {
        Document changelog = parse(SERVICE_CHANGELOG);

        String sql = elements(changelog, "sql").stream()
                .map(Element::getTextContent)
                .collect(Collectors.joining("\n"));

        assertThat(sql).contains("idx_search_document_fts");
        assertThat(sql).contains("using gin (to_tsvector('simple'");
        assertThat(sql).contains("title_");
        assertThat(sql).contains("summary_");
        assertThat(sql).contains("content_");
        assertThat(sql).contains("tags_");
    }

    private static void assertTableMatchesEntity(String tableName, Class<?> entityType) {
        Document changelog = parse(SERVICE_CHANGELOG);
        Table table = entityType.getAnnotation(Table.class);
        assertThat(table).as("JPA table mapping on %s", entityType.getSimpleName()).isNotNull();
        assertThat(table.name()).isEqualTo(tableName);

        Element createTable = elements(changelog, "createTable").stream()
                .filter(element -> tableName.equals(element.getAttribute("tableName")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing createTable for " + tableName));

        Map<String, ColumnDefinition> declaredColumns = new LinkedHashMap<>();
        for (Element column : childElements(createTable, "column")) {
            declaredColumns.put(column.getAttribute("name"), parseColumn(column));
        }
        Map<String, Field> entityColumns = entityColumns(entityType);

        assertThat(declaredColumns.keySet())
                .containsExactlyInAnyOrderElementsOf(entityColumns.keySet());

        for (Map.Entry<String, Field> entry : entityColumns.entrySet()) {
            Field field = entry.getValue();
            Column column = field.getAnnotation(Column.class);
            ColumnDefinition definition = declaredColumns.get(entry.getKey());
            assertThat(definition).as("column %s", entry.getKey()).isNotNull();

            if (field.isAnnotationPresent(Id.class) || !column.nullable()) {
                assertThat(definition.nullable()).as("nullable of %s", entry.getKey()).isFalse();
            }
            if (!column.columnDefinition().isEmpty()) {
                assertThat(definition.type()).as("type of %s", entry.getKey())
                        .isEqualTo(column.columnDefinition().toLowerCase(Locale.ROOT));
            } else if (field.getType() == String.class && column.length() > 0) {
                assertThat(definition.type()).as("type of %s", entry.getKey())
                        .isEqualTo("varchar(" + column.length() + ")");
            }
        }

        long primaryKeys = declaredColumns.values().stream()
                .filter(ColumnDefinition::primaryKey)
                .count();
        assertThat(primaryKeys).isEqualTo(1);
        String idColumn = entityColumns.entrySet().stream()
                .filter(entry -> entry.getValue().isAnnotationPresent(Id.class))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing @Id column on " + entityType.getSimpleName()));
        assertThat(declaredColumns.get(idColumn).primaryKey()).isTrue();

        assertThat(declaredUniqueConstraints(changelog, tableName))
                .isEqualTo(entityUniqueConstraints(table));
        assertThat(declaredIndexes(changelog, tableName))
                .isEqualTo(entityIndexes(table));
    }

    private static Map<String, Field> entityColumns(Class<?> entityType) {
        Map<String, Field> columns = new LinkedHashMap<>();
        for (Field field : entityType.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (column != null) {
                columns.put(column.name(), field);
            }
        }
        return columns;
    }

    private static Map<String, Set<String>> declaredUniqueConstraints(Document changelog, String tableName) {
        Map<String, Set<String>> constraints = new LinkedHashMap<>();
        for (Element element : elements(changelog, "addUniqueConstraint")) {
            if (tableName.equals(element.getAttribute("tableName"))) {
                constraints.put(element.getAttribute("constraintName"),
                        splitColumns(element.getAttribute("columnNames")));
            }
        }
        return constraints;
    }

    private static Map<String, Set<String>> entityUniqueConstraints(Table table) {
        Map<String, Set<String>> constraints = new LinkedHashMap<>();
        for (UniqueConstraint constraint : table.uniqueConstraints()) {
            constraints.put(constraint.name(), new TreeSet<>(List.of(constraint.columnNames())));
        }
        return constraints;
    }

    private static Map<String, IndexDefinition> declaredIndexes(Document changelog, String tableName) {
        Map<String, IndexDefinition> indexes = new LinkedHashMap<>();
        for (Element element : elements(changelog, "createIndex")) {
            if (tableName.equals(element.getAttribute("tableName"))) {
                List<String> columns = childElements(element, "column").stream()
                        .map(column -> column.getAttribute("name"))
                        .toList();
                indexes.put(element.getAttribute("indexName"),
                        new IndexDefinition(new TreeSet<>(columns)));
            }
        }
        return indexes;
    }

    private static Map<String, IndexDefinition> entityIndexes(Table table) {
        Map<String, IndexDefinition> indexes = new LinkedHashMap<>();
        for (Index index : table.indexes()) {
            indexes.put(index.name(), new IndexDefinition(splitColumns(index.columnList())));
        }
        return indexes;
    }

    private static ColumnDefinition parseColumn(Element column) {
        String type = column.getAttribute("type").toLowerCase(Locale.ROOT);
        boolean nullable = true;
        boolean primaryKey = false;
        for (Element constraints : childElements(column, "constraints")) {
            if ("false".equalsIgnoreCase(constraints.getAttribute("nullable"))) {
                nullable = false;
            }
            if ("true".equalsIgnoreCase(constraints.getAttribute("primaryKey"))) {
                primaryKey = true;
            }
        }
        return new ColumnDefinition(type, nullable, primaryKey);
    }

    private static Set<String> splitColumns(String columnList) {
        Set<String> columns = new TreeSet<>();
        for (String column : columnList.split(",")) {
            String normalized = column.trim();
            if (!normalized.isEmpty()) {
                columns.add(normalized);
            }
        }
        return columns;
    }

    private static Document parse(String classpathLocation) {
        try (InputStream input = SearchChangelogTests.class.getClassLoader()
                .getResourceAsStream(classpathLocation)) {
            assertThat(input).as("classpath resource %s", classpathLocation).isNotNull();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(input);
        } catch (Exception exception) {
            throw new AssertionError("Cannot parse changelog " + classpathLocation, exception);
        }
    }

    private static List<Element> elements(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        List<Element> elements = new ArrayList<>(nodes.getLength());
        for (int index = 0; index < nodes.getLength(); index++) {
            elements.add((Element) nodes.item(index));
        }
        return elements;
    }

    private static List<Element> childElements(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        List<Element> elements = new ArrayList<>(nodes.getLength());
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    private record ColumnDefinition(String type, boolean nullable, boolean primaryKey) {
    }

    private record IndexDefinition(Set<String> columns) {
    }
}