package com.study.app.config;

import com.study.app.command.ItemService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxDispatcher {

    private final ItemService service;

    public OutboxDispatcher(ItemService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 100)
    public void dispatch() {
        try {
            service.dispatchCompletion();
        } catch (RuntimeException e) {
            // swallow; retried next tick
        }
    }
}
