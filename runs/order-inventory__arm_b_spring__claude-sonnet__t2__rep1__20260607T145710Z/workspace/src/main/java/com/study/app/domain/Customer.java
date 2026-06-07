package com.study.app.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private long balance;

    protected Customer() {}

    public Customer(String name, long balance) {
        this.name = name;
        this.balance = balance;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public long getBalance() { return balance; }
}
