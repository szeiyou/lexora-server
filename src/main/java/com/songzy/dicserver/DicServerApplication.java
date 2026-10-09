package com.songzy.dicserver;

import com.songzy.dicserver.config.LlmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.songzy.dicserver.repository")
@EnableScheduling
@EnableConfigurationProperties(LlmProperties.class)
public class DicServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DicServerApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(120000);
        return new RestTemplate(factory);
    }

    @Bean
    @Qualifier("qwen3TtsRestTemplate")
    public RestTemplate qwen3TtsRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(300000);
        return new RestTemplate(factory);
    }

    @Bean
    @Qualifier("mimoTtsRestTemplate")
    public RestTemplate mimoTtsRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(300000);
        return new RestTemplate(factory);
    }
}
