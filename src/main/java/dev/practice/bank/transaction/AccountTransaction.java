package dev.practice.bank.transaction;

import dev.practice.bank.account.Account;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Um lançamento do extrato. É imutável: nunca se edita nem se apaga um lançamento.
 * O valor é sempre positivo; o tipo diz se entrou ou saiu dinheiro.
 */
@Entity
@Table(name = "account_transactions", indexes = @Index(columnList = "account_id, occurredAt"))
public class AccountTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false, length = 140)
    private String description;

    /** Liga os dois lançamentos (saída e entrada) de uma mesma transferência. */
    private UUID transferId;

    @Column(nullable = false)
    private Instant occurredAt;

    protected AccountTransaction() {}

    private AccountTransaction(Account account, TransactionType type, BigDecimal amount,
                               String description, UUID transferId, Instant occurredAt) {
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = account.getBalance();
        this.description = description;
        this.transferId = transferId;
        this.occurredAt = occurredAt;
    }

    /**
     * Aplica o crédito ou débito na conta e devolve o lançamento correspondente,
     * já com o saldo resultante. Assim saldo e extrato nunca ficam diferentes.
     */
    public static AccountTransaction apply(Account account, TransactionType type, BigDecimal amount,
                                           String description, UUID transferId, Instant occurredAt) {
        if (type.isCredit()) {
            account.credit(amount);
        } else {
            account.debit(amount);
        }
        return new AccountTransaction(account, type, amount, description, transferId, occurredAt);
    }

    public Long getId() { return id; }
    public Account getAccount() { return account; }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getDescription() { return description; }
    public UUID getTransferId() { return transferId; }
    public Instant getOccurredAt() { return occurredAt; }
}
