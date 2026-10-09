package com.songzy.dicserver;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class TtsRestTemplateConfigTest {

    @Test
    void qwen3TtsRestTemplateShouldUseLongerReadTimeoutThanDefaultRestTemplate() {
        DicServerApplication app = new DicServerApplication();

        RestTemplate defaultTemplate = app.restTemplate();
        RestTemplate qwen3TtsTemplate = app.qwen3TtsRestTemplate();

        SimpleClientHttpRequestFactory defaultFactory =
                (SimpleClientHttpRequestFactory) defaultTemplate.getRequestFactory();
        SimpleClientHttpRequestFactory qwen3Factory =
                (SimpleClientHttpRequestFactory) qwen3TtsTemplate.getRequestFactory();

        assertThat(ReflectionTestUtils.getField(defaultFactory, "readTimeout")).isEqualTo(120000);
        assertThat(ReflectionTestUtils.getField(qwen3Factory, "readTimeout")).isEqualTo(300000);
    }

    @Test
    void mimoTtsRestTemplateShouldUseLongerReadTimeoutThanDefaultRestTemplate() {
        DicServerApplication app = new DicServerApplication();

        RestTemplate mimoTtsTemplate = app.mimoTtsRestTemplate();

        SimpleClientHttpRequestFactory mimoFactory =
                (SimpleClientHttpRequestFactory) mimoTtsTemplate.getRequestFactory();

        assertThat(ReflectionTestUtils.getField(mimoFactory, "readTimeout")).isEqualTo(300000);
        assertThat(ReflectionTestUtils.getField(mimoFactory, "connectTimeout")).isEqualTo(5000);
    }
}
