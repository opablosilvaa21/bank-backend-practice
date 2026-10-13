package dev.practice.bank.transfer;

import dev.practice.bank.shared.BusinessRuleException;

public class InsufficientBalanceException extends BusinessRuleException {
    public InsufficientBalanceException(Long accountId) {
        super("Saldo insuficiente na conta: " + accountId);
    }
}
