package com.study.app.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int balance;

    @Version
    private long version;

    protected Customer() {}

    public Customer(String name, int balance) {
        this.name = name;
        this.balance = balance;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public int getBalance() { return balance; }
}
