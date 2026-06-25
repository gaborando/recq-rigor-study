package com.study.app.config;

import com.study.app.command.ListService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxDispatcher {

    private final ListService service;

    public OutboxDispatcher(ListService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 100)
    public void dispatch() {
        try {
            service.dispatchNotifications();
        } catch (RuntimeException e) {
            // swallow; retried next tick
        }
    }
}
