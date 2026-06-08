package com.study.app.command;

import com.study.app.domain.Product;
import com.study.app.domain.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
public class ProductCommandService {

    private final ProductRepository products;

    public ProductCommandService(ProductRepository products) {
        this.products = products;
    }

    @Transactional
    public Product create(String name, int unitPrice, int stock) {
        return products.save(new Product(name, unitPrice, stock));
    }

    @Transactional
    public void restock(UUID id, int units) {
        if (!products.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found");
        }
        products.addStock(id, units);
    }

    @Transactional
    public boolean reserve(UUID productId, int quantity) {
        return products.reserveStock(productId, quantity) > 0;
    }

    @Transactional
    public void release(UUID productId, int quantity) {
        products.releaseStock(productId, quantity);
    }
}
