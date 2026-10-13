package dev.practice.bank.account;

import dev.practice.bank.shared.ResourceNotFoundException;

public class AccountNotFoundException extends ResourceNotFoundException {

    public AccountNotFoundException(Long id) {
        super("Conta não encontrada: " + id);
    }
}
