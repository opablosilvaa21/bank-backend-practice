package dev.practice.bank.account;

import java.math.BigDecimal;
import java.time.Instant;

/** Contrato público da conta. Desacopla a API da entidade JPA. */
public record AccountResponse(
        Long id,
        Long customerId,
        String ownerName,
        String agency,
        String accountNumber,
        AccountType type,
        AccountStatus status,
        BigDecimal balance,
        Instant openedAt
) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(
                a.getId(), a.getCustomer().getId(), a.getOwnerName(), a.getAgency(), a.getAccountNumber(),
                a.getType(), a.getStatus(), a.getBalance(), a.getOpenedAt());
    }
}
