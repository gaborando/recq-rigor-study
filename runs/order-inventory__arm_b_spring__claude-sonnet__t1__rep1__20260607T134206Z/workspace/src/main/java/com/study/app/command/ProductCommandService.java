package com.study.app.command;

import com.study.app.domain.Product;
import com.study.app.domain.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
public class ProductCommandService {

    @Autowired
    private ProductRepository productRepository;

    @Transactional
    public Product createProduct(String name, int unitPrice, int stock) {
        return productRepository.save(new Product(name, unitPrice, stock));
    }

    @Transactional
    public void restock(UUID id, int units) {
        if (!productRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found");
        }
        productRepository.addStock(id, units);
    }
}
