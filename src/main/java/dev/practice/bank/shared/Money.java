package dev.practice.bank.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Regras de valor monetário usadas em toda movimentação. */
public final class Money {

    public static final String CURRENCY = "BRL";

    private Money() {}

    /**
     * Valida que o valor é positivo e tem no máximo 2 casas decimais.
     * Devolve o valor com escala 2 (10 vira 10.00). Nunca arredonda.
     */
    public static BigDecimal requireValidAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("O valor deve ser maior que zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("O valor deve ter no máximo 2 casas decimais");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
