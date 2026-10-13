package dev.practice.bank.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        Long id,
        Long accountId,
        TransactionType type,
        String direction,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        UUID transferId,
        Instant occurredAt
) {
    public static TransactionResponse from(AccountTransaction t) {
        return new TransactionResponse(
                t.getId(), t.getAccount().getId(), t.getType(),
                t.getType().isCredit() ? "CREDITO" : "DEBITO",
                t.getAmount(), t.getBalanceAfter(), t.getDescription(), t.getTransferId(), t.getOccurredAt());
    }
}
