package cn.tolim.pudans.notification.changelog;

import cn.tolim.pudans.notification.entity.NotificationDeadLetterEntity;
import cn.tolim.pudans.notification.entity.NotificationProviderEntity;
import cn.tolim.pudans.notification.entity.NotificationSendRecordEntity;
import cn.tolim.pudans.notification.entity.NotificationTemplateEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.parser.ChangeLogParser;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.hibernate.annotations.SoftDelete;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Verifies the notification Liquibase changelog covers every mapped entity table and column.
class NotificationChangelogTests {

    private static final String NAMESPACE = "http://www.liquibase.org/xml/ns/dbchangelog";
    private static final String MASTER = "notification/changelog-master.xml";
    private static final String CHANGELOG = "notification/1.0.0-notification-changelog.xml";
    private static final String TABLE_PREFIX_VARIABLE = "${pudans_table_prefix}";

    private static final List<Class<?>> ENTITIES = List.of(NotificationTemplateEntity.class,
            NotificationProviderEntity.class, NotificationSendRecordEntity.class,
            NotificationDeadLetterEntity.class);

    @Test
    void liquibaseParsesMasterChangelogWithIncludedChangeSet() throws Exception {
        ResourceAccessor accessor = new ClassLoaderResourceAccessor();
        ChangeLogParser parser = ChangeLogParserFactory.getInstance().getParser("xml", accessor);
        DatabaseChangeLog changeLog = parser.parse(MASTER, new ChangeLogParameters(), accessor);

        assertThat(changeLog.getChangeSets()).hasSize(1);
        assertThat(changeLog.getChangeSets().getFirst().getId()).isEqualTo("1.0.0-notification");
        assertThat(changeLog.getChangeSets().getFirst().getAuthor()).isEqualTo("PUDANS");
    }

    @Test
    void masterIncludesVersionedChangelog() throws Exception {
        Document master = load(MASTER);

        List<String> includes = new ArrayList<>();
        NodeList nodes = master.getElementsByTagNameNS(NAMESPACE, "include");
        for (int index = 0; index < nodes.getLength(); index++) {
            includes.add(((Element) nodes.item(index)).getAttribute("file"));
        }

        assertThat(includes).containsExactly("1.0.0-notification-changelog.xml");
    }

    @Test
    void changelogDeclaresEveryEntityTableAndColumn() throws Exception {
        Map<String, Element> tables = tables();

        for (Class<?> entity : ENTITIES) {
            Table table = entity.getAnnotation(Table.class);
            assertThat(table).as("JPA table mapping for %s", entity.getSimpleName()).isNotNull();
            assertThat(tables).containsKey(table.name());

            Set<String> declaredColumns = columns(tables.get(table.name()));
            assertThat(declaredColumns)
                    .as("columns of %s", table.name())
                    .containsAll(persistentColumns(entity));
            assertThat(declaredColumns).as("soft delete column of %s", table.name())
                    .contains(softDeleteColumn(entity));
            assertThat(declaredColumns).as("primary key of %s", table.name()).contains("id_");
        }
    }

    @Test
    void changelogDeclaresEntityIndexesAndUniqueConstraints() throws Exception {
        Document changelog = load(CHANGELOG);
        Map<String, Element> tables = tables(changelog);

        for (Class<?> entity : ENTITIES) {
            Table table = entity.getAnnotation(Table.class);
            Element tableElement = tables.get(table.name());
            assertThat(tableElement).isNotNull();

            for (Index index : table.indexes()) {
                Element indexElement = findIndex(changelog, table.name(), index.name());
                assertThat(indexElement).as("index %s", index.name()).isNotNull();
                assertThat(columns(indexElement))
                        .containsExactlyElementsOf(splitColumnList(index.columnList()));
            }

            for (UniqueConstraint constraint : table.uniqueConstraints()) {
                Element constraintElement =
                        findUniqueConstraint(changelog, table.name(), constraint.name());
                assertThat(constraintElement).as("unique constraint %s", constraint.name())
                        .isNotNull();
                assertThat(splitColumnList(constraintElement.getAttribute("columnNames")))
                        .containsExactlyElementsOf(splitColumnList(
                                String.join(",", constraint.columnNames())));
            }
        }
    }

    private static Map<String, Element> tables() throws Exception {
        return tables(load(CHANGELOG));
    }

    private static Map<String, Element> tables(Document document) {
        String prefix = propertyValue(document, "pudans_table_prefix");
        Map<String, Element> tables = new LinkedHashMap<>();
        NodeList nodes = document.getElementsByTagNameNS(NAMESPACE, "createTable");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            tables.put(resolve(element.getAttribute("tableName"), prefix), element);
        }
        return tables;
    }

    private static Element findIndex(Document document, String tableName, String indexName) {
        String prefix = propertyValue(document, "pudans_table_prefix");
        NodeList nodes = document.getElementsByTagNameNS(NAMESPACE, "createIndex");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            if (indexName.equals(element.getAttribute("indexName"))
                    && tableName.equals(resolve(element.getAttribute("tableName"), prefix))) {
                return element;
            }
        }
        return null;
    }

    private static Element findUniqueConstraint(Document document, String tableName,
                                                String constraintName) {
        String prefix = propertyValue(document, "pudans_table_prefix");
        NodeList nodes = document.getElementsByTagNameNS(NAMESPACE, "addUniqueConstraint");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            if (constraintName.equals(element.getAttribute("constraintName"))
                    && tableName.equals(resolve(element.getAttribute("tableName"), prefix))) {
                return element;
            }
        }
        return null;
    }

    private static Set<String> columns(Element table) {
        Set<String> columns = new LinkedHashSet<>();
        NodeList nodes = table.getElementsByTagNameNS(NAMESPACE, "column");
        for (int index = 0; index < nodes.getLength(); index++) {
            columns.add(((Element) nodes.item(index)).getAttribute("name"));
        }
        return columns;
    }

    private static Set<String> persistentColumns(Class<?> entity) {
        Set<String> columns = new LinkedHashSet<>();
        for (Class<?> type = entity; type != null && type != Object.class;
             type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                Column column = field.getAnnotation(Column.class);
                if (column != null) {
                    columns.add(column.name());
                }
            }
            for (Method method : type.getDeclaredMethods()) {
                Column column = method.getAnnotation(Column.class);
                if (column != null) {
                    columns.add(column.name());
                }
            }
        }
        return columns;
    }

    private static String softDeleteColumn(Class<?> entity) {
        SoftDelete softDelete = entity.getAnnotation(SoftDelete.class);
        assertThat(softDelete).as("soft delete mapping for %s", entity.getSimpleName())
                .isNotNull();
        return softDelete.columnName();
    }

    private static List<String> splitColumnList(String columnList) {
        List<String> columns = new ArrayList<>();
        for (String column : columnList.split(",")) {
            columns.add(column.trim());
        }
        return columns;
    }

    private static String propertyValue(Document document, String name) {
        NodeList nodes = document.getElementsByTagNameNS(NAMESPACE, "property");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            if (name.equals(element.getAttribute("name"))) {
                return element.getAttribute("value");
            }
        }
        return "";
    }

    private static String resolve(String value, String prefix) {
        return value.replace(TABLE_PREFIX_VARIABLE, prefix);
    }

    private static Document load(String resource) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream stream = NotificationChangelogTests.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertThat(stream).as("classpath resource %s", resource).isNotNull();
            return factory.newDocumentBuilder().parse(stream);
        }
    }
}