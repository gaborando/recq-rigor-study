package com.study.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class OrdersApp {
    public static void main(String[] args) {
        SpringApplication.run(OrdersApp.class, args);
    }
}
