package dev.practice.bank.customer;

import dev.practice.bank.shared.ResourceNotFoundException;

public class CustomerNotFoundException extends ResourceNotFoundException {
    public CustomerNotFoundException(Long id) {
        super("Cliente não encontrado: " + id);
    }
}
