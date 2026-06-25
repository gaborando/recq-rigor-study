package com.study.app.command;

import com.study.app.domain.Charge;
import com.study.app.domain.Customer;
import com.study.app.query.ChargeRepository;
import com.study.app.query.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    public enum ChargeResult { SUCCESS, INSUFFICIENT_FUNDS, UNKNOWN_CUSTOMER }

    private final CustomerRepository customers;
    private final ChargeRepository charges;

    public PaymentService(CustomerRepository customers, ChargeRepository charges) {
        this.customers = customers;
        this.charges = charges;
    }

    @Transactional
    public Customer createCustomer(String name, long balance) {
        return customers.save(new Customer(UUID.randomUUID().toString(), name, balance));
    }

    @Transactional(readOnly = true)
    public Optional<Customer> getCustomer(String id) {
        return customers.findById(id);
    }

    /** Returns true if the customer exists and the credit was applied. */
    @Transactional
    public boolean deposit(String id, long amount) {
        return customers.credit(id, amount) > 0;
    }

    /**
     * Charge a customer exactly once per bookingId. The customer row is locked so
     * concurrent charges against one balance serialise (no double-spend, balance
     * never negative). Idempotent: a replay returns the recorded outcome.
     */
    @Transactional
    public ChargeResult charge(String bookingId, String customerId, long amount) {
        Optional<Customer> opt = customers.lockById(customerId);
        if (opt.isEmpty()) return ChargeResult.UNKNOWN_CUSTOMER;

        // Re-check under the customer lock so duplicate charges are idempotent.
        Optional<Charge> existing = charges.findById(bookingId);
        if (existing.isPresent()) {
            return Charge.SUCCESS.equals(existing.get().getStatus())
                    ? ChargeResult.SUCCESS : ChargeResult.INSUFFICIENT_FUNDS;
        }

        Customer customer = opt.get();
        if (customer.getBalance() >= amount) {
            customer.setBalance(customer.getBalance() - amount);
            charges.save(new Charge(bookingId, customerId, amount, Charge.SUCCESS));
            return ChargeResult.SUCCESS;
        }
        charges.save(new Charge(bookingId, customerId, amount, Charge.FAILED));
        return ChargeResult.INSUFFICIENT_FUNDS;
    }

    /** Compensation backstop: reverse a successful charge exactly once. */
    @Transactional
    public void refund(String bookingId) {
        Optional<Charge> opt = charges.findById(bookingId);
        if (opt.isEmpty()) return;
        Charge charge = opt.get();
        if (!Charge.SUCCESS.equals(charge.getStatus()) || charge.isRefunded()) return;
        Optional<Customer> customer = customers.lockById(charge.getCustomerId());
        if (customer.isEmpty()) return;
        customer.get().setBalance(customer.get().getBalance() + charge.getAmount());
        charge.setRefunded(true);
    }
}
