package dev.practice.bank.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Corpo de depósito e saque. */
public record MovementRequest(
        @NotNull(message = "é obrigatório")
        @DecimalMin(value = "0.00", inclusive = false, message = "deve ser maior que zero")
        @Digits(integer = 17, fraction = 2, message = "deve ter no máximo 2 casas decimais")
        BigDecimal amount,
        @Size(max = 140, message = "deve ter no máximo 140 caracteres")
        String description
) {}
