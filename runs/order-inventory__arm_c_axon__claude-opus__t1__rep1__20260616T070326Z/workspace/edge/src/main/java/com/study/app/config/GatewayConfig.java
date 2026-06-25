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
     * Retrying gateway for commands that race on a single aggregate (restock /
     * deposit): an optimistic-lock ConcurrencyException is transient and is
     * retried until it commits, so no update is lost.
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

    /**
     * Non-retrying gateway for idempotent creates (notably duplicate order
     * submissions): a duplicate-create conflict must be dropped, not retried.
     */
    @Bean(name = "plainCommandGateway")
    public CommandGateway plainCommandGateway(CommandBus commandBus) {
        return DefaultCommandGateway.builder()
                .commandBus(commandBus)
                .build();
    }
}
