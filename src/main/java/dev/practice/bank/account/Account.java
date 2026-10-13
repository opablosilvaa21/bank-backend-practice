package dev.practice.bank.account;

import dev.practice.bank.customer.Customer;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts", uniqueConstraints = @UniqueConstraint(columnNames = {"agency", "accountNumber"}))
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false, length = 4)
    private String agency;

    @Column(nullable = false, length = 12)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ATIVA;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);

    @Column(nullable = false)
    private Instant openedAt;

    protected Account() {}

    /** Toda conta nasce com saldo zero. Dinheiro só entra por movimentação (crédito). */
    public Account(Customer customer, String agency, String accountNumber, AccountType type, Instant openedAt) {
        this.customer = customer;
        this.agency = agency;
        this.accountNumber = accountNumber;
        this.type = type;
        this.openedAt = openedAt;
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public String getOwnerName() { return customer.getFullName(); }
    public String getAgency() { return agency; }
    public String getAccountNumber() { return accountNumber; }
    public AccountType getType() { return type; }
    public AccountStatus getStatus() { return status; }
    public BigDecimal getBalance() { return balance; }
    public Instant getOpenedAt() { return openedAt; }

    public boolean isActive() {
        return status == AccountStatus.ATIVA;
    }

    /** Contas bloqueadas ou encerradas não podem movimentar. */
    public void ensureActive() {
        if (!isActive()) {
            throw new AccountNotActiveException(id, status);
        }
    }

    public void block() {
        this.status = AccountStatus.BLOQUEADA;
    }

    public void close() {
        if (balance.signum() != 0) {
            throw new IllegalStateException("Só é possível encerrar conta com saldo zero");
        }
        this.status = AccountStatus.ENCERRADA;
    }

    public boolean hasSufficientBalance(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    /** Debita o valor. Nunca deixa o saldo ficar negativo. */
    public void debit(BigDecimal amount) {
        requirePositive(amount);
        if (!hasSufficientBalance(amount)) {
            throw new IllegalStateException("Saldo insuficiente para débito");
        }
        this.balance = this.balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        requirePositive(amount);
        this.balance = this.balance.add(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("O valor deve ser maior que zero");
        }
    }
}
