package dev.practice.bank.customer;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    /** Só dígitos. Nunca sai completo pela API: use Cpf.mask. */
    @Column(nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false)
    private Instant createdAt;

    protected Customer() {}

    public Customer(String fullName, String cpf, String email, LocalDate birthDate, Instant createdAt) {
        this.fullName = fullName;
        this.cpf = cpf;
        this.email = email;
        this.birthDate = birthDate;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getCpf() { return cpf; }
    public String getEmail() { return email; }
    public LocalDate getBirthDate() { return birthDate; }
    public Instant getCreatedAt() { return createdAt; }
}
