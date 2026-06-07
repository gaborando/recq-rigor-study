package com.study.app.command;

import com.study.app.domain.Customer;
import com.study.app.domain.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
public class CustomerCommandService {

    @Autowired
    private CustomerRepository customerRepository;

    @Transactional
    public Customer createCustomer(String name, long balance) {
        return customerRepository.save(new Customer(name, balance));
    }

    @Transactional
    public void deposit(UUID id, long amount) {
        if (!customerRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found");
        }
        customerRepository.addBalance(id, amount);
    }
}
