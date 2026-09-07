package com.xtong.saas.system.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 System 模块只在 XML 中保存复杂 SQL，并为七个 Mapper 提供匹配资源。 */
class MapperSqlBoundaryTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");

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
