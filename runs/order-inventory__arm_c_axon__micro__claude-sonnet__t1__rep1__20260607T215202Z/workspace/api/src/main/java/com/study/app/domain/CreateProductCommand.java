package com.study.app.domain;
public record CreateProductCommand(String productId, String name, int unitPrice, int stock) {}
