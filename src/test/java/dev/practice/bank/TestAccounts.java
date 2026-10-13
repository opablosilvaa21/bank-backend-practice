package dev.practice.bank;

import dev.practice.bank.account.Account;
import dev.practice.bank.account.AccountType;
import dev.practice.bank.customer.Customer;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Monta contas em memória para testes unitários, sem banco de dados. */
public final class TestAccounts {

    private TestAccounts() {}

    public static Account account(Long id, String balance) {
        Customer customer = new Customer("Titular " + id, "0000000000" + (id % 10), "titular" + id + "@gmail.com",
                LocalDate.of(1990, 1, 1), Instant.now());
        ReflectionTestUtils.setField(customer, "id", id);
        Account account = new Account(customer, "0001", "1000" + id + "-0", AccountType.CORRENTE, Instant.now());
        ReflectionTestUtils.setField(account, "id", id);
        BigDecimal initial = new BigDecimal(balance);
        if (initial.signum() > 0) {
            account.credit(initial);
        }
        return account;
    }
}
