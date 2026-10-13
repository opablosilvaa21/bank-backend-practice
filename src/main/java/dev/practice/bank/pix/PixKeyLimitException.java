package dev.practice.bank.pix;

import dev.practice.bank.shared.BusinessRuleException;

public class PixKeyLimitException extends BusinessRuleException {
    public PixKeyLimitException(int limit) {
        super("A conta já atingiu o limite de " + limit + " chaves Pix");
    }
}
