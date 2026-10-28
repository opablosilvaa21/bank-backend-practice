package dev.practice.bank.transfer;

import dev.practice.bank.shared.BusinessRuleException;

public class IdempotencyKeyReusedException extends BusinessRuleException {
    public IdempotencyKeyReusedException() {
        super("Esta Idempotency-Key já foi usada em uma operação com outros dados");
    }
}
