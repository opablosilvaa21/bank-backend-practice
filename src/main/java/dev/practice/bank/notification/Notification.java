package dev.practice.bank.notification;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private UUID transferId;

    @Column(nullable = false, length = 200)
    private String message;

    @Column(nullable = false)
    private Instant createdAt;

    protected Notification() {}

    public Notification(Long accountId, UUID transferId, String message, Instant createdAt) {
        this.accountId = accountId;
        this.transferId = transferId;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public UUID getTransferId() { return transferId; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
