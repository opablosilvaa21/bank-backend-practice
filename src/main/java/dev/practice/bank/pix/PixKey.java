package dev.practice.bank.pix;

import dev.practice.bank.account.Account;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "pix_keys")
public class PixKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PixKeyType keyType;

    /** Valor já normalizado: CPF só com dígitos, e-mail e chave aleatória em minúsculas. */
    @Column(nullable = false, unique = true, length = 77)
    private String keyValue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false)
    private Instant createdAt;

    protected PixKey() {}

    public PixKey(PixKeyType keyType, String keyValue, Account account, Instant createdAt) {
        this.keyType = keyType;
        this.keyValue = keyValue;
        this.account = account;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public PixKeyType getKeyType() { return keyType; }
    public String getKeyValue() { return keyValue; }
    public Account getAccount() { return account; }
    public Instant getCreatedAt() { return createdAt; }
}
