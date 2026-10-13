package dev.practice.bank.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID transferId,
        TransferChannel channel,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        String currency,
        String description,
        Instant executedAt
) {}
