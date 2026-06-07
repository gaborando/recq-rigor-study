package com.study.app.query;

import com.study.app.domain.Customer;
import com.study.app.domain.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
public class CustomerQueryService {

    @Autowired
    private CustomerRepository customerRepository;

    public Customer getCustomer(UUID id) {
        return customerRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found"));
    }
}
