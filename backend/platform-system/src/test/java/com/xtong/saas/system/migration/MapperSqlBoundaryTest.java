package com.xtong.saas.system.migration;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 System 模块只在 XML 中保存复杂 SQL，并为七个 Mapper 提供匹配资源。 */
class MapperSqlBoundaryTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path MAPPER_RESOURCES = Path.of("src/main/resources/mapper");
    private static final Map<String, String> EXPECTED_MAPPERS = Map.of(
            "bootstrap/SystemBootstrapLockMapper.xml", "com.xtong.saas.system.bootstrap.mapper.SystemBootstrapLockMapper",
            "tenant/SystemTenantMapper.xml", "com.xtong.saas.system.tenant.mapper.SystemTenantMapper",
            "user/SystemUserMapper.xml", "com.xtong.saas.system.user.mapper.SystemUserMapper",
            "role/SystemRoleMapper.xml", "com.xtong.saas.system.role.mapper.SystemRoleMapper",
            "menu/SystemMenuMapper.xml", "com.xtong.saas.system.menu.mapper.SystemMenuMapper",
            "role/SystemUserRoleMapper.xml", "com.xtong.saas.system.role.mapper.SystemUserRoleMapper",
            "role/SystemRoleMenuMapper.xml", "com.xtong.saas.system.role.mapper.SystemRoleMenuMapper");

    @Test
    void javaSourcesShouldNotContainMyBatisSqlAnnotationsProvidersOrScripts() throws IOException {
        String sources = readAll(MAIN_JAVA.resolve("com/xtong/saas/system"), ".java");

        assertThat(sources.lines().filter(line -> line.matches(
                        ".*(import\\s+|@)org\\.apache\\.ibatis\\.annotations\\."
                                + "(Select|Insert|Update|Delete)(Provider)?\\b.*")
                        || line.contains("<script>") || line.contains("</script>")))
                .as("MyBatis SQL annotations, providers or scripts in System Java sources")
                .isEmpty();
    }

    @Test
    void everySystemMapperShouldHaveMatchingXmlNamespace() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        for (Map.Entry<String, String> expected : EXPECTED_MAPPERS.entrySet()) {
            Path xml = MAPPER_RESOURCES.resolve(expected.getKey());
            assertThat(xml).as("XML resource for %s", expected.getValue()).isRegularFile();
            Element mapper = factory.newDocumentBuilder().parse(xml.toFile()).getDocumentElement();
            assertThat(mapper.getTagName()).isEqualTo("mapper");
            assertThat(mapper.getAttribute("namespace")).isEqualTo(expected.getValue());
            assertPrecedingComment(mapper, xml + " file responsibility");
            for (Node child = mapper.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element statement
                        && Set.of("select", "insert", "update", "delete").contains(statement.getTagName())) {
                    assertPrecedingComment(statement, xml + "#" + statement.getAttribute("id"));
                }
            }
        }
    }

    /** 要求文件职责与每条 SQL 前有非空说明，忽略排版空白而不约束 SQL 文本。 */
    private static void assertPrecedingComment(Node node, String description) {
        Node previous = node.getPreviousSibling();
        while (previous != null && previous.getNodeType() == Node.TEXT_NODE
                && previous.getTextContent().isBlank()) {
            previous = previous.getPreviousSibling();
        }
        assertThat(previous).as("Comment before %s", description).isNotNull();
        assertThat(previous.getNodeType()).as("Comment before %s", description).isEqualTo(Node.COMMENT_NODE);
        assertThat(previous.getTextContent()).as("Comment content for %s", description).isNotBlank();
    }

    @Test
    void systemQueriesShouldUseLambdaColumnReferences() throws IOException {
        String sources = readAll(MAIN_JAVA.resolve("com/xtong/saas/system"), ".java");

        assertThat(sources)
                .doesNotContain("new QueryWrapper")
                .doesNotMatch("(?s).*\\.(eq|ne|gt|ge|lt|le|like|orderByAsc|orderByDesc)\\([^\\n]*\\\"[a-z_]+\\\".*");
    }

    /** 读取指定目录下匹配后缀的全部源码，以便实施跨实现类的查询形态约束。 */
    private static String readAll(Path directory, String suffix) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .map(MapperSqlBoundaryTest::readUtf8)
                    .collect(java.util.stream.Collectors.joining("\n"));
        }
    }

    /** 以 UTF-8 读取单个源码文件，并将受检异常转换为测试运行异常。 */
    private static String readUtf8(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read source file: " + path, exception);
        }
    }
}
