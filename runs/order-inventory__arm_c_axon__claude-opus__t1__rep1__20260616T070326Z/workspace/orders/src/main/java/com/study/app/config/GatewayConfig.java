package com.study.app.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.commandhandling.gateway.IntervalRetryScheduler;
import org.axonframework.commandhandling.gateway.RetryScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class GatewayConfig {

    /**
     * The saga dispatches reserve/charge/confirm/release commands that race on
     * single product/customer aggregates. Optimistic-lock ConcurrencyExceptions
     * are transient and retried so reservations/charges serialize without loss.
     */
    @Bean
    @Primary
    public CommandGateway retryingCommandGateway(CommandBus commandBus) {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(8);
        RetryScheduler retryScheduler = IntervalRetryScheduler.builder()
                .retryExecutor(executor)
                .maxRetryCount(500)
                .retryInterval(15)
                .build();
        return DefaultCommandGateway.builder()
                .commandBus(commandBus)
                .retryScheduler(retryScheduler)
                .build();
    }
}
