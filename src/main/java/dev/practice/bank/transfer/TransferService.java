package dev.practice.bank.transfer;

import dev.practice.bank.account.Account;
import dev.practice.bank.account.AccountNotFoundException;
import dev.practice.bank.account.AccountRepository;
import dev.practice.bank.shared.Money;
import dev.practice.bank.transaction.AccountTransaction;
import dev.practice.bank.transaction.AccountTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountRepository accounts;
    private final AccountTransactionRepository transactions;
    private final TransferRepository transfers;
    private final ApplicationEventPublisher events;

    public TransferService(AccountRepository accounts, AccountTransactionRepository transactions,
                           TransferRepository transfers, ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.transfers = transfers;
        this.events = events;
    }

    /** Transferência sem Idempotency-Key: cada chamada movimenta dinheiro. */
    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        return transfer(request, null).response();
    }

    @Transactional
    public TransferResult transfer(TransferRequest request, String idempotencyKey) {
        return execute(request.fromAccountId(), request.toAccountId(), request.amount(),
                TransferChannel.TRANSFERENCIA, request.description(), idempotencyKey);
    }

    /**
     * Núcleo de toda transferência (TED interna ou Pix).
     * Debita a origem, credita o destino e grava a transferência e os dois lançamentos na mesma transação.
     * Com Idempotency-Key já usada para o mesmo pedido, não movimenta nada e devolve a transferência original.
     */
    @Transactional
    public TransferResult execute(Long fromId, Long toId, BigDecimal rawAmount, TransferChannel channel,
                                  String rawDescription, String idempotencyKey) {
        if (fromId == null || toId == null) {
            throw new IllegalArgumentException("As contas de origem e destino são obrigatórias");
        }
        // Revalida aqui: o Service não deve confiar só na validação do Controller.
        BigDecimal amount = Money.requireValidAmount(rawAmount);
        if (fromId.equals(toId)) {
            throw new SameAccountTransferException();
        }
        String key = IdempotencyKey.validate(idempotencyKey);
        String description = StringUtils.hasText(rawDescription) ? rawDescription.trim() : null;

        // Trava as contas sempre na mesma ordem, com o menor ID primeiro.
        // Assim, A->B e B->A ao mesmo tempo não causam deadlock.
        Account first = lock(Math.min(fromId, toId));
        Account second = lock(Math.max(fromId, toId));
        Account from = first.getId().equals(fromId) ? first : second;
        Account to = from == first ? second : first;

        // A chave é conferida DEPOIS do lock: um pedido repetido em paralelo espera o primeiro terminar
        // e então encontra a transferência já gravada, em vez de executar de novo.
        String requestHash = key == null ? null : IdempotencyKey.fingerprint(channel, fromId, toId, amount, description);
        if (key != null) {
            var previous = transfers.findByIdempotencyKey(key);
            if (previous.isPresent()) {
                return replay(previous.get(), requestHash);
            }
        }

        from.ensureActive();
        to.ensureActive();
        if (!from.hasSufficientBalance(amount)) {
            throw new InsufficientBalanceException(fromId);
        }

        // O banco guarda microssegundos: truncar aqui faz a resposta repetida sair idêntica à original.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Transfer transfer = new Transfer(channel, fromId, toId, amount, description, now, key, requestHash);
        transfers.save(transfer);

        String note = description != null ? " - " + description : "";
        String sentDescription = truncate(channel.sentPrefix() + to.getOwnerName() + note);
        String receivedDescription = truncate(channel.receivedPrefix() + from.getOwnerName() + note);

        // apply() debita/credita a conta e cria o lançamento com o saldo resultante.
        // As contas são gerenciadas pelo JPA: o UPDATE do saldo acontece no commit.
        UUID transferId = transfer.getId();
        transactions.saveAll(List.of(
                AccountTransaction.apply(from, channel.sentType(), amount, sentDescription, transferId, now),
                AccountTransaction.apply(to, channel.receivedType(), amount, receivedDescription, transferId, now)));

        // Quem escuta decide quando reagir: o @TransactionalEventListener só recebe depois do commit.
        events.publishEvent(new TransferCompletedEvent(transferId, channel, fromId, from.getOwnerName(),
                toId, to.getOwnerName(), amount, Money.CURRENCY, now));

        // Log sem nome do titular nem saldo: só IDs e valor.
        log.info("{} concluído: id={} origem={} destino={} valor={}", channel, transferId, fromId, toId, amount);
        return new TransferResult(transfer.toResponse(), false);
    }

    private static TransferResult replay(Transfer previous, String requestHash) {
        if (!previous.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        log.info("{} repetido com a mesma Idempotency-Key: devolvendo id={}", previous.getChannel(), previous.getId());
        return new TransferResult(previous.toResponse(), true);
    }

    private Account lock(Long id) {
        return accounts.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private static String truncate(String text) {
        return text.length() <= 140 ? text : text.substring(0, 140);
    }
}
