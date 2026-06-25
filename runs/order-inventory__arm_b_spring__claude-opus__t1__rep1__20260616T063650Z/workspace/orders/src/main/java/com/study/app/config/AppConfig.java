package com.study.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
public class AppConfig {

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
        factory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** Bounded worker pool for async order processing. */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(8);
        ex.setMaxPoolSize(16);
        ex.setQueueCapacity(2000);
        ex.setThreadNamePrefix("order-worker-");
        ex.initialize();
        return ex;
    }
}
