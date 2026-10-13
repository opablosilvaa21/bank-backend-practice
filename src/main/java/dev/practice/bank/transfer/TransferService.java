package dev.practice.bank.transfer;

import dev.practice.bank.account.Account;
import dev.practice.bank.account.AccountNotFoundException;
import dev.practice.bank.account.AccountRepository;
import dev.practice.bank.shared.Money;
import dev.practice.bank.transaction.AccountTransaction;
import dev.practice.bank.transaction.AccountTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountRepository accounts;
    private final AccountTransactionRepository transactions;

    public TransferService(AccountRepository accounts, AccountTransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        return execute(request.fromAccountId(), request.toAccountId(), request.amount(),
                TransferChannel.TRANSFERENCIA, request.description());
    }

    /**
     * Núcleo de toda transferência (TED interna ou Pix).
     * Debita a origem, credita o destino e grava os dois lançamentos na mesma transação.
     */
    @Transactional
    public TransferResponse execute(Long fromId, Long toId, BigDecimal rawAmount,
                                    TransferChannel channel, String description) {
        if (fromId == null || toId == null) {
            throw new IllegalArgumentException("As contas de origem e destino são obrigatórias");
        }
        // Revalida aqui: o Service não deve confiar só na validação do Controller.
        BigDecimal amount = Money.requireValidAmount(rawAmount);
        if (fromId.equals(toId)) {
            throw new SameAccountTransferException();
        }

        // Trava as contas sempre na mesma ordem, com o menor ID primeiro.
        // Assim, A->B e B->A ao mesmo tempo não causam deadlock.
        Account first = lock(Math.min(fromId, toId));
        Account second = lock(Math.max(fromId, toId));
        Account from = first.getId().equals(fromId) ? first : second;
        Account to = from == first ? second : first;

        from.ensureActive();
        to.ensureActive();
        if (!from.hasSufficientBalance(amount)) {
            throw new InsufficientBalanceException(fromId);
        }

        UUID transferId = UUID.randomUUID();
        Instant now = Instant.now();
        String note = StringUtils.hasText(description) ? " - " + description.trim() : "";
        String sentDescription = truncate(channel.sentPrefix() + to.getOwnerName() + note);
        String receivedDescription = truncate(channel.receivedPrefix() + from.getOwnerName() + note);

        // apply() debita/credita a conta e cria o lançamento com o saldo resultante.
        // As contas são gerenciadas pelo JPA: o UPDATE do saldo acontece no commit.
        transactions.saveAll(List.of(
                AccountTransaction.apply(from, channel.sentType(), amount, sentDescription, transferId, now),
                AccountTransaction.apply(to, channel.receivedType(), amount, receivedDescription, transferId, now)));

        // Log sem nome do titular nem saldo: só IDs e valor.
        log.info("{} concluído: id={} origem={} destino={} valor={}", channel, transferId, fromId, toId, amount);
        return new TransferResponse(transferId, channel, fromId, toId, amount, Money.CURRENCY,
                StringUtils.hasText(description) ? description.trim() : null, now);
    }

    private Account lock(Long id) {
        return accounts.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private static String truncate(String text) {
        return text.length() <= 140 ? text : text.substring(0, 140);
    }
}
