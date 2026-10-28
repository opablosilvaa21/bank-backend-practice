package dev.practice.bank.transfer;

import dev.practice.bank.shared.Money;
import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro de uma transferência. É imutável, como os lançamentos do extrato.
 * O ID é gerado aqui (e não pelo banco) porque os lançamentos precisam dele antes do INSERT.
 */
@Entity
@Table(name = "transfers")
public class Transfer implements Persistable<UUID> {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferChannel channel;

    @Column(nullable = false)
    private Long fromAccountId;

    @Column(nullable = false)
    private Long toAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 140)
    private String description;

    @Column(nullable = false)
    private Instant executedAt;

    @Column(length = 64, unique = true)
    private String idempotencyKey;

    @Column(length = 64)
    private String requestHash;

    /**
     * Com ID preenchido, o Spring Data acharia que a entidade já existe e faria um SELECT antes do INSERT.
     * Este campo diz que ela é nova até ser gravada ou lida do banco.
     */
    @Transient
    private boolean isNew = true;

    protected Transfer() {}

    public Transfer(TransferChannel channel, Long fromAccountId, Long toAccountId, BigDecimal amount,
                    String description, Instant executedAt, String idempotencyKey, String requestHash) {
        this.id = UUID.randomUUID();
        this.channel = channel;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.description = description;
        this.executedAt = executedAt;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public UUID getId() { return id; }
    public TransferChannel getChannel() { return channel; }
    public Long getFromAccountId() { return fromAccountId; }
    public Long getToAccountId() { return toAccountId; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public Instant getExecutedAt() { return executedAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }

    public TransferResponse toResponse() {
        return new TransferResponse(id, channel, fromAccountId, toAccountId, amount, Money.CURRENCY,
                description, executedAt);
    }
}
