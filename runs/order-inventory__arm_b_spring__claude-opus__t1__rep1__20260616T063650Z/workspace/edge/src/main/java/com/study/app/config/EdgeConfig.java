package com.study.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** RestClient beans for each owning service the edge forwards to. */
@Configuration
public class EdgeConfig {

    @Bean
    public RestClient ordersRestClient(@Value("${peer.orders.url}") String url) {
        return peerClient(url);
    }

    @Bean
    public RestClient inventoryRestClient(@Value("${peer.inventory.url}") String url) {
        return peerClient(url);
    }

    @Bean
    public RestClient customersRestClient(@Value("${peer.customers.url}") String url) {
        return peerClient(url);
    }

    private static RestClient peerClient(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
