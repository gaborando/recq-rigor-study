package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class CreateProductCommand extends DomainCommand {
    private String productId;
    private String name;
    private int unitPrice;
    private int stock;

    public CreateProductCommand() {}

    public CreateProductCommand(String productId, String name, int unitPrice, int stock) {
        this.productId = productId;
        this.name = name;
        this.unitPrice = unitPrice;
        this.stock = stock;
    }

    @Override
    public String getAggregateId() { return productId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getUnitPrice() { return unitPrice; }
    public void setUnitPrice(int unitPrice) { this.unitPrice = unitPrice; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
}
