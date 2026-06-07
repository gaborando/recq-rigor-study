package com.study.app.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.commandhandling.gateway.IntervalRetryScheduler;
import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.Executors;

@Configuration
public class AxonConfig {

    @Bean
    @Primary
    public CommandGateway commandGateway(CommandBus commandBus) {
        IntervalRetryScheduler retryScheduler = IntervalRetryScheduler.builder()
                .retryExecutor(Executors.newScheduledThreadPool(4))
                .retryInterval(50)
                .maxRetryCount(25)
                // don't retry duplicate-creation or business-logic failures
                .addNonTransientFailurePredicate(AggregateStreamCreationException.class, ex -> true)
                .addNonTransientFailurePredicate(IllegalArgumentException.class, ex -> true)
                .build();
        return DefaultCommandGateway.builder()
                .commandBus(commandBus)
                .retryScheduler(retryScheduler)
                .build();
    }
}
