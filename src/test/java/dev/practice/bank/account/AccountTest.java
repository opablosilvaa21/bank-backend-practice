package dev.practice.bank.account;

import dev.practice.bank.TestAccounts;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private final Account account = TestAccounts.account(1L, "100.00");

    @Test
    void debitaECredita() {
        account.debit(new BigDecimal("30.00"));
        account.credit(new BigDecimal("5.50"));
        assertThat(account.getBalance()).isEqualByComparingTo("75.50");
    }

    @Test
    void debitoMaiorQueOSaldoFalhaSemAlterarSaldo() {
        assertThatThrownBy(() -> account.debit(new BigDecimal("100.01")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void contaBloqueadaNaoMovimenta() {
        account.block();
        assertThatThrownBy(account::ensureActive).isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    void soEncerraContaComSaldoZero() {
        assertThatThrownBy(account::close).isInstanceOf(IllegalStateException.class);
        account.debit(new BigDecimal("100.00"));
        account.close();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ENCERRADA);
    }

    @Test
    void recusaValoresZeroNegativosOuNulos() {
        assertThatThrownBy(() -> account.debit(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.credit(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.credit(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }
}
