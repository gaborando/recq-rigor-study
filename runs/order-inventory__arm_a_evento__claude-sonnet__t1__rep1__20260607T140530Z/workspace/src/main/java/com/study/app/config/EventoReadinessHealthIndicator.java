package com.study.app.config;

import com.evento.application.EventoBundle;
import com.study.app.web.OrderInvoker;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports DOWN until the Evento bundle has registered its handlers with the server.
 * This prevents app.sh from marking the app as ready before it can handle commands.
 */
@Component
public class EventoReadinessHealthIndicator implements HealthIndicator {

    private volatile boolean ready = false;

    public EventoReadinessHealthIndicator(EventoBundle eventoBundle) {
        Thread.ofVirtual().start(() -> {
            long deadline = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    OrderInvoker invoker = eventoBundle.getInvoker(OrderInvoker.class);
                    invoker.getStats();
                    ready = true;
                    return;
                } catch (Exception ignored) {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
    }

    @Override
    public Health health() {
        return ready
                ? Health.up().build()
                : Health.down().withDetail("status", "waiting for Evento bundle registration").build();
    }
}
