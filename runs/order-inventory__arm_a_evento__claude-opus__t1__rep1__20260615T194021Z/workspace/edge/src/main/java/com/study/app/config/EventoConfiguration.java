package com.study.app.config;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.study.app.EdgeApp;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Pre-wired Evento bootstrap for the edge bundle — do not modify.
 * Edge is a stateless API gateway: it only acts as an @Invoker (sends commands
 * and dispatches queries through the gateways). It owns no aggregates,
 * projectors, sagas or observers, so it has no consumers and needs no database
 * or consumer state store — the default consumer engine is left in place.
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
            BeanFactory factory) throws Exception {
        return EventoBundle.Builder.builder()
                .setBasePackage(EdgeApp.class.getPackage())
                .setBundleId(bundleId)
                .setBundleVersion(version)
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                        new ClusterNodeAddress(host, port)))
                .setInjector(factory::getBean)
                .start();
    }
}
