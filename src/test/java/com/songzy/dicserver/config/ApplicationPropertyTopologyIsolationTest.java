package com.songzy.dicserver.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationPropertyTopologyIsolationTest {

    @Test
    void mainApplicationPropertiesDoNotEmbedRuntimeLlmTopology() throws IOException {
        Properties properties = loadProperties("src/main/resources/application.properties");

        assertThat(properties.stringPropertyNames())
                .doesNotContain("llm.providers[0].name")
                .doesNotContain("llm.api." + "choice")
                .doesNotContain("llm." + "fallback." + "enabled")
                .doesNotContain("llm." + "fallback." + "order");
        assertThat(properties.stringPropertyNames())
                .noneMatch(name -> name.startsWith("llm.deepseek."))
                .noneMatch(name -> name.startsWith("llm.bailian."))
                .noneMatch(name -> name.startsWith("llm.tencent."));
        assertThat(properties.getProperty("llm.prompt.template")).isNotBlank();
    }

    @Test
    void testApplicationPropertiesProvideTestOnlyLlmTopology() throws IOException {
        Properties properties = loadProperties("src/test/resources/application-test.properties");

        assertThat(properties.getProperty("llm.providers[0].name")).isEqualTo("test-openai");
        assertThat(properties.getProperty("llm.providers[0].protocol")).isEqualTo("openai");
        assertThat(properties.getProperty("llm.providers[0].api-key")).isEqualTo("test-api-key");
    }

    private Properties loadProperties(String path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of(path), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
