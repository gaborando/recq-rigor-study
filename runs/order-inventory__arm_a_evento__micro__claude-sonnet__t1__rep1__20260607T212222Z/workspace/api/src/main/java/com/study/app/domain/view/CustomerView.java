package com.study.app.domain.view;

import com.evento.common.modeling.messaging.payload.View;

public class CustomerView implements View {
    private String id;
    private String name;
    private int balance;

    public CustomerView() {}

    public CustomerView(String id, String name, int balance) {
        this.id = id;
        this.name = name;
        this.balance = balance;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getBalance() { return balance; }
    public void setBalance(int balance) { this.balance = balance; }
}
