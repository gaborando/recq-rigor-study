package com.study.app.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer {
    @Id
    private String id;
    private String name;
    private long balance;

    protected Customer() {}

    public Customer(String id, String name, long balance) {
        this.id = id;
        this.name = name;
        this.balance = balance;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public long getBalance() { return balance; }
    public void setBalance(long balance) { this.balance = balance; }
}
