package com.study.app.command;

public record CreateProductCommand(String productId, String name, int unitPrice, int stock) {}
