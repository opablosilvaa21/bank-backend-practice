package dev.practice.bank.shared;

/** Base para regra de negócio violada (ex.: saldo insuficiente). O handler converte em HTTP 422. */
public abstract class BusinessRuleException extends RuntimeException {
    protected BusinessRuleException(String message) {
        super(message);
    }
}
