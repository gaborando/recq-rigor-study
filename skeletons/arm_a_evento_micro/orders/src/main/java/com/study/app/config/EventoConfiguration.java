package com.study.app.config;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.common.messaging.consumer.ConsumerProcessor;
import com.evento.common.serialization.ObjectMapperUtils;
import com.evento.consumer.state.store.jdbc.FlywayMigrator;
import com.evento.consumer.state.store.jdbc.JdbcConsumerLock;
import com.evento.consumer.state.store.jdbc.JdbcConsumerStateStore;
import com.evento.consumer.state.store.jdbc.JdbcDeadEventQueue;
import com.evento.consumer.state.store.jdbc.JdbcDedupeStore;
import com.evento.consumer.state.store.jdbc.JdbcSagaStateStore;
import com.evento.consumer.state.store.jdbc.SqlDialect;
import com.study.app.OrdersApp;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import javax.sql.DataSource;
import java.util.concurrent.Executors;

/**
 * Pre-wired Evento bootstrap for the orders bundle — do not modify.
 * Components under com.study.app are discovered automatically; cross-bundle
 * commands/queries/events route through the Evento server. Consumers use the
 * DURABLE JDBC consumer state store backed by this service's own PostgreSQL
 * (checkpoints/saga/DLQ/dedup survive a restart; tables auto-created by Flyway).
 */
@Configuration
public class EventoConfiguration {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    public EventoBundle eventoApplication(
            @Value("${evento.server.host}") String host,
            @Value("${evento.server.port}") int port,
            @Value("${evento.bundle.id}") String bundleId,
            @Value("${evento.bundle.version}") long version,
            DataSource dataSource,
            BeanFactory factory) throws Exception {
        return EventoBundle.Builder.builder()
                .setBasePackage(OrdersApp.class.getPackage())
                .setBundleId(bundleId)
                .setBundleVersion(version)
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                        new ClusterNodeAddress(host, port)))
                .setInjector(factory::getBean)
                .setConsumerEngineConfigBuilder((eventoServer, performanceService) -> {
                    try {
                        FlywayMigrator.migrate(dataSource, SqlDialect.POSTGRES);
                        var om = ObjectMapperUtils.getPayloadObjectMapper();
                        var lock = new JdbcConsumerLock(dataSource, SqlDialect.POSTGRES);
                        var stateStore = new JdbcConsumerStateStore(dataSource, SqlDialect.POSTGRES);
                        var sagaStateStore = new JdbcSagaStateStore(dataSource, SqlDialect.POSTGRES, om);
                        var deadEventQueue = new JdbcDeadEventQueue(dataSource, SqlDialect.POSTGRES, om);
                        var dedupeStore = new JdbcDedupeStore(dataSource, SqlDialect.POSTGRES);
                        var processor = ConsumerProcessor.builder()
                                .eventoServer(eventoServer)
                                .lock(lock)
                                .stateStore(stateStore)
                                .sagaStateStore(sagaStateStore)
                                .deadEventQueue(deadEventQueue)
                                .dedupeStore(dedupeStore)
                                .performanceService(performanceService)
                                .objectMapper(om)
                                .observerExecutor(Executors.newVirtualThreadPerTaskExecutor())
                                .build();
                        return new ConsumerEngineConfig(processor, stateStore, deadEventQueue);
                    } catch (Exception e) {
                        throw new RuntimeException("failed to wire JDBC consumer state store", e);
                    }
                })
                .start();
    }
}
