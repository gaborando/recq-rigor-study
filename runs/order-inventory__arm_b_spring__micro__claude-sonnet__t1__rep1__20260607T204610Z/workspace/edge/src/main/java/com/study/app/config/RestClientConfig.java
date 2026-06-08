package com.study.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean("ordersClient")
    public RestClient ordersClient(@Value("${orders.url}") String ordersUrl) {
        return RestClient.builder().baseUrl(ordersUrl).build();
    }

    @Bean("inventoryClient")
    public RestClient inventoryClient(@Value("${inventory.url}") String inventoryUrl) {
        return RestClient.builder().baseUrl(inventoryUrl).build();
    }

    @Bean("customersClient")
    public RestClient customersClient(@Value("${customers.url}") String customersUrl) {
        return RestClient.builder().baseUrl(customersUrl).build();
    }
}
