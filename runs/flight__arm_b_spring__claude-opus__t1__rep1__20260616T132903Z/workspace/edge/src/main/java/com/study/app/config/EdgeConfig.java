package com.study.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class EdgeConfig {

    @Bean
    public RestClient flightsClient(@Value("${FLIGHTS_URL:http://localhost:8081}") String url) {
        return client(url);
    }

    @Bean
    public RestClient bookingsClient(@Value("${BOOKINGS_URL:http://localhost:8082}") String url) {
        return client(url);
    }

    @Bean
    public RestClient paymentsClient(@Value("${PAYMENTS_URL:http://localhost:8083}") String url) {
        return client(url);
    }

    private RestClient client(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
