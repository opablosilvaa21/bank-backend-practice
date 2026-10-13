package dev.practice.bank.config;

import dev.practice.bank.account.Account;
import dev.practice.bank.account.AccountRepository;
import dev.practice.bank.account.AccountStatus;
import dev.practice.bank.account.AccountType;
import dev.practice.bank.customer.Customer;
import dev.practice.bank.customer.CustomerRepository;
import dev.practice.bank.pix.PixKeyRepository;
import dev.practice.bank.shared.Cpf;
import dev.practice.bank.transaction.AccountTransaction;
import dev.practice.bank.transaction.AccountTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Garante que a base fictícia gerada é coerente. */
@SpringBootTest
@Transactional
class DemoDataIntegrationTest {

    @Autowired CustomerRepository customers;
    @Autowired AccountRepository accounts;
    @Autowired AccountTransactionRepository transactions;
    @Autowired PixKeyRepository pixKeys;

    @Test
    void criaClientesFixosMaisOsGerados() {
        assertThat(customers.count()).isEqualTo(3 + 97);
        assertThat(accounts.findById(1L).orElseThrow().getBalance()).isEqualByComparingTo("1500.00");
        assertThat(accounts.findById(2L).orElseThrow().getBalance()).isEqualByComparingTo("850.00");
        assertThat(accounts.findById(3L).orElseThrow().getBalance()).isEqualByComparingTo("3200.00");
    }

    @Test
    void saldoDeCadaContaEhASomaDoExtrato() {
        for (Account account : accounts.findAll()) {
            List<AccountTransaction> history = transactions.findByAccountIdOrderByIdAsc(account.getId());
            assertThat(history).as("conta %d sem extrato", account.getId()).isNotEmpty();

            BigDecimal sum = BigDecimal.ZERO;
            for (AccountTransaction t : history) {
                sum = t.getType().isCredit() ? sum.add(t.getAmount()) : sum.subtract(t.getAmount());
                assertThat(sum).as("saldo negativo no extrato da conta %d", account.getId())
                        .isGreaterThanOrEqualTo(BigDecimal.ZERO);
                assertThat(t.getBalanceAfter()).isEqualByComparingTo(sum);
            }
            assertThat(account.getBalance()).as("conta %d", account.getId()).isEqualByComparingTo(sum);
        }
    }

    @Test
    void cpfsEEmailsSaoValidosEUnicos() {
        List<Customer> all = customers.findAll();
        assertThat(all).allMatch(c -> Cpf.isValid(c.getCpf()));
        assertThat(all).extracting(Customer::getCpf).doesNotHaveDuplicates();
        assertThat(all).extracting(Customer::getEmail).doesNotHaveDuplicates().allMatch(e -> e.endsWith("@gmail.com"));
    }

    @Test
    void temVariedadeDeTiposEStatus() {
        List<Account> all = accounts.findAll();
        assertThat(all).anyMatch(a -> a.getType() == AccountType.POUPANCA);
        assertThat(all).anyMatch(a -> a.getStatus() == AccountStatus.BLOQUEADA);
        assertThat(all).anyMatch(a -> a.getStatus() == AccountStatus.ENCERRADA);
        assertThat(all).filteredOn(a -> a.getStatus() == AccountStatus.ENCERRADA)
                .allMatch(a -> a.getBalance().signum() == 0);
    }

    @Test
    void todoClienteTemChavePixCpf() {
        assertThat(pixKeys.count()).isGreaterThanOrEqualTo(customers.count());
    }
}
