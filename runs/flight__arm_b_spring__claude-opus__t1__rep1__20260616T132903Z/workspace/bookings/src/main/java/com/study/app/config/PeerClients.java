package com.study.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.util.concurrent.Executor;

@Configuration
@EnableScheduling
@EnableAsync
public class PeerClients {

    @Bean
    public RestClient flightsClient(@Value("${FLIGHTS_URL:http://localhost:8081}") String url) {
        return restClient(url);
    }

    @Bean
    public RestClient paymentsClient(@Value("${PAYMENTS_URL:http://localhost:8083}") String url) {
        return restClient(url);
    }

    private RestClient restClient(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Bean(name = "sagaExecutor")
    public Executor sagaExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(16);
        ex.setMaxPoolSize(64);
        ex.setQueueCapacity(1000);
        ex.setThreadNamePrefix("saga-");
        ex.initialize();
        return ex;
    }
}
