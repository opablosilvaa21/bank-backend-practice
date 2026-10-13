package dev.practice.bank.transaction;

import dev.practice.bank.account.Account;
import dev.practice.bank.account.AccountNotFoundException;
import dev.practice.bank.account.AccountRepository;
import dev.practice.bank.shared.Money;
import dev.practice.bank.transfer.InsufficientBalanceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class TransactionService {

    /** Fuso usado para interpretar as datas do filtro de extrato. */
    public static final ZoneId BANK_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final AccountRepository accounts;
    private final AccountTransactionRepository transactions;

    public TransactionService(AccountRepository accounts, AccountTransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional
    public TransactionResponse deposit(Long accountId, MovementRequest request) {
        BigDecimal amount = Money.requireValidAmount(request.amount());
        Account account = lockActive(accountId);
        AccountTransaction tx = AccountTransaction.apply(account, TransactionType.DEPOSITO, amount,
                describe(request.description(), "Depósito"), null, Instant.now());
        transactions.save(tx);
        log.info("Depósito: conta={} valor={}", accountId, amount);
        return TransactionResponse.from(tx);
    }

    @Transactional
    public TransactionResponse withdraw(Long accountId, MovementRequest request) {
        BigDecimal amount = Money.requireValidAmount(request.amount());
        Account account = lockActive(accountId);
        if (!account.hasSufficientBalance(amount)) {
            throw new InsufficientBalanceException(accountId);
        }
        AccountTransaction tx = AccountTransaction.apply(account, TransactionType.SAQUE, amount,
                describe(request.description(), "Saque"), null, Instant.now());
        transactions.save(tx);
        log.info("Saque: conta={} valor={}", accountId, amount);
        return TransactionResponse.from(tx);
    }

    /**
     * Extrato do período, do mais recente para o mais antigo.
     * Sem datas: últimos 90 dias. As datas são inclusivas e usam o fuso de São Paulo.
     */
    @Transactional(readOnly = true)
    public Page<TransactionResponse> statement(Long accountId, LocalDate from, LocalDate to, Pageable pageable) {
        if (!accounts.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        LocalDate end = to != null ? to : LocalDate.now(BANK_ZONE);
        LocalDate start = from != null ? from : end.minusDays(90);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("A data inicial deve ser anterior ou igual à data final");
        }
        Instant startInstant = start.atStartOfDay(BANK_ZONE).toInstant();
        Instant endInstant = end.plusDays(1).atStartOfDay(BANK_ZONE).toInstant();

        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
        return transactions
                .findByAccountIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                        accountId, startInstant, endInstant, sorted)
                .map(TransactionResponse::from);
    }

    private Account lockActive(Long accountId) {
        Account account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        account.ensureActive();
        return account;
    }

    private static String describe(String description, String fallback) {
        return StringUtils.hasText(description) ? description.trim() : fallback;
    }
}
