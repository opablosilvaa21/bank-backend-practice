package dev.practice.bank.customer;

import dev.practice.bank.shared.Cpf;

import java.time.Instant;
import java.time.LocalDate;

/** O CPF sai sempre mascarado. */
public record CustomerResponse(
        Long id,
        String fullName,
        String cpf,
        String email,
        LocalDate birthDate,
        Instant createdAt
) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(
                c.getId(), c.getFullName(), Cpf.mask(c.getCpf()), c.getEmail(), c.getBirthDate(), c.getCreatedAt());
    }
}
