package dev.practice.bank.account;

import dev.practice.bank.shared.BusinessRuleException;

public class AccountNotActiveException extends BusinessRuleException {
    public AccountNotActiveException(Long id, AccountStatus status) {
        super("A conta " + id + " não pode movimentar: status " + status);
    }
}
