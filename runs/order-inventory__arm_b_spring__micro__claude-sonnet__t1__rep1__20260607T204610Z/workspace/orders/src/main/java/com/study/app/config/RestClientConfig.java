package com.study.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean("inventoryRestClient")
    public RestClient inventoryRestClient(@Value("${inventory.url}") String inventoryUrl) {
        return RestClient.builder().baseUrl(inventoryUrl).build();
    }

    @Bean("customersRestClient")
    public RestClient customersRestClient(@Value("${customers.url}") String customersUrl) {
        return RestClient.builder().baseUrl(customersUrl).build();
    }
}
