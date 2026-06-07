package com.study.app.config;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.study.app.App;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Pre-wired Evento bootstrap — do not modify. Components annotated with
 * {@code @Aggregate}, {@code @Projector}, {@code @Projection}, {@code @Saga},
 * {@code @Service}, {@code @Observer} and {@code @Invoker} under
 * {@code com.study.app} are discovered and registered automatically.
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
            @Value("${evento.strict.confinement:${EVENTO_STRICT:false}}") boolean strictConfinement,
            BeanFactory factory) throws Exception {
        return EventoBundle.Builder.builder()
                .setBasePackage(App.class.getPackage())
                .setBundleId(bundleId)
                .setBundleVersion(version)
                .setStrictConfinement(strictConfinement)
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                        new ClusterNodeAddress(host, port)))
                .setInjector(factory::getBean)
                .start();
    }
}
