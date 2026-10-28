package dev.practice.bank.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Publicado quando uma transferência (TED interna ou Pix) é gravada.
 * Carrega tudo o que quem escuta precisa, para ninguém ter que consultar o banco de novo.
 * Não é publicado em repetições com a mesma Idempotency-Key: a transferência é a mesma.
 */
public record TransferCompletedEvent(
        UUID transferId,
        TransferChannel channel,
        Long fromAccountId,
        String fromOwnerName,
        Long toAccountId,
        String toOwnerName,
        BigDecimal amount,
        String currency,
        Instant executedAt
) {}
