package com.study.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class PeerClients {

    private RestClient build(String baseUrl) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(20).toMillis());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Bean
    public RestClient listsClient(@Value("${LISTS_URL:http://localhost:8081}") String url) {
        return build(url);
    }

    @Bean
    public RestClient itemsClient(@Value("${ITEMS_URL:http://localhost:8082}") String url) {
        return build(url);
    }

    @Bean
    public RestClient notificationsClient(@Value("${NOTIFICATIONS_URL:http://localhost:8083}") String url) {
        return build(url);
    }
}
