package dev.practice.bank.transfer;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record TransferRequest(
        @NotNull(message = "é obrigatório") @Positive(message = "deve ser positivo") Long fromAccountId,
        @NotNull(message = "é obrigatório") @Positive(message = "deve ser positivo") Long toAccountId,
        @NotNull(message = "é obrigatório")
        @DecimalMin(value = "0.00", inclusive = false, message = "deve ser maior que zero")
        @Digits(integer = 17, fraction = 2, message = "deve ter no máximo 2 casas decimais")
        BigDecimal amount,
        @Size(max = 140, message = "deve ter no máximo 140 caracteres")
        String description
) {
    /** Construtor de conveniência sem descrição. */
    public TransferRequest(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        this(fromAccountId, toAccountId, amount, null);
    }
}
