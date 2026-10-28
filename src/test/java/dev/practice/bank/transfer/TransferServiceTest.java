package dev.practice.bank.transfer;

import dev.practice.bank.TestAccounts;
import dev.practice.bank.account.Account;
import dev.practice.bank.account.AccountNotActiveException;
import dev.practice.bank.transaction.AccountTransaction;
import dev.practice.bank.transaction.AccountTransactionRepository;
import dev.practice.bank.transaction.TransactionType;
import dev.practice.bank.account.AccountNotFoundException;
import dev.practice.bank.account.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    AccountRepository repository;

    @Mock
    AccountTransactionRepository transactions;

    @Mock
    TransferRepository transfers;

    TransferService service;
    Account ana;
    Account bruno;

    @BeforeEach
    void setUp() {
        service = new TransferService(repository, transactions, transfers);
        ana = account(1L, "1500.00");
        bruno = account(2L, "850.00");
    }

    @Test
    void transfereEAtualizaOsDoisSaldos() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(ana));
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(bruno));

        TransferResponse response = service.transfer(new TransferRequest(1L, 2L, new BigDecimal("200.50")));

        assertThat(ana.getBalance()).isEqualByComparingTo("1299.50");
        assertThat(bruno.getBalance()).isEqualByComparingTo("1050.50");
        assertThat(response.amount()).isEqualByComparingTo("200.50");
        assertThat(response.fromAccountId()).isEqualTo(1L);
        assertThat(response.toAccountId()).isEqualTo(2L);
        assertThat(response.currency()).isEqualTo("BRL");
    }

    @Test
    void permiteTransferirOSaldoInteiro() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(ana));
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(bruno));

        service.transfer(new TransferRequest(1L, 2L, new BigDecimal("1500.00")));

        assertThat(ana.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void recusaSaldoInsuficienteSemAlterarSaldos() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(ana));
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(bruno));

        assertThatThrownBy(() -> service.transfer(new TransferRequest(1L, 2L, new BigDecimal("1500.01"))))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(ana.getBalance()).isEqualByComparingTo("1500.00");
        assertThat(bruno.getBalance()).isEqualByComparingTo("850.00");
    }

    @Test
    void recusaContaDeOrigemInexistente() {
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(bruno));
        when(repository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer(new TransferRequest(99L, 2L, BigDecimal.TEN)))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("99");
        assertThat(bruno.getBalance()).isEqualByComparingTo("850.00");
    }

    @Test
    void recusaContaDeDestinoInexistente() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(ana));
        when(repository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer(new TransferRequest(1L, 99L, BigDecimal.TEN)))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("99");
        assertThat(ana.getBalance()).isEqualByComparingTo("1500.00");
    }

    @Test
    void recusaTransferenciaParaAMesmaConta() {
        assertThatThrownBy(() -> service.transfer(new TransferRequest(1L, 1L, BigDecimal.TEN)))
                .isInstanceOf(SameAccountTransferException.class);
        verify(repository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void recusaValorZeroOuNegativoMesmoSemValidacaoDoController() {
        assertThatThrownBy(() -> service.transfer(new TransferRequest(1L, 2L, BigDecimal.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.transfer(new TransferRequest(1L, 2L, new BigDecimal("-5"))))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void recusaMaisDeDuasCasasDecimaisSemArredondar() {
        assertThatThrownBy(() -> service.transfer(new TransferRequest(1L, 2L, new BigDecimal("10.005"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 casas decimais");
        verifyNoInteractions(repository);
    }

    @Test
    void padronizaOValorComDuasCasasDecimais() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(ana));
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(bruno));

        TransferResponse response = service.transfer(new TransferRequest(1L, 2L, new BigDecimal("10")));

        assertThat(response.amount()).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void gravaOsDoisLancamentosNoExtratoComOMesmoTransferId() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(ana));
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(bruno));

        TransferResponse response = service.transfer(new TransferRequest(1L, 2L, new BigDecimal("100.00"), "Aluguel"));

        ArgumentCaptor<List<AccountTransaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactions).saveAll(captor.capture());
        List<AccountTransaction> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getType()).isEqualTo(TransactionType.TRANSFERENCIA_ENVIADA);
        assertThat(saved.get(0).getBalanceAfter()).isEqualByComparingTo("1400.00");
        assertThat(saved.get(0).getDescription()).isEqualTo("Transferência enviada para Titular 2 - Aluguel");
        assertThat(saved.get(1).getType()).isEqualTo(TransactionType.TRANSFERENCIA_RECEBIDA);
        assertThat(saved.get(1).getBalanceAfter()).isEqualByComparingTo("950.00");
        assertThat(saved).allMatch(t -> t.getTransferId().equals(response.transferId()));
    }

    @Test
    void recusaContaBloqueadaSemAlterarSaldos() {
        bruno.block();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(ana));
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(bruno));

        assertThatThrownBy(() -> service.transfer(new TransferRequest(1L, 2L, BigDecimal.TEN)))
                .isInstanceOf(AccountNotActiveException.class);

        assertThat(ana.getBalance()).isEqualByComparingTo("1500.00");
        assertThat(bruno.getBalance()).isEqualByComparingTo("850.00");
        verifyNoInteractions(transactions);
    }

    @Test
    void travaAsContasSempreNaOrdemCrescenteDeId() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(ana));
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(bruno));

        // Transferência de 2 para 1: mesmo assim a conta 1 é travada primeiro.
        service.transfer(new TransferRequest(2L, 1L, BigDecimal.TEN));

        InOrder order = inOrder(repository);
        order.verify(repository).findByIdForUpdate(1L);
        order.verify(repository).findByIdForUpdate(2L);
        assertThat(bruno.getBalance()).isEqualByComparingTo("840.00");
        assertThat(ana.getBalance()).isEqualByComparingTo("1510.00");
    }

    @Test
    void gravaATransferenciaComAChaveDeIdempotencia() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(ana));
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(bruno));
        when(transfers.findByIdempotencyKey("chave-1")).thenReturn(Optional.empty());

        TransferResult result = service.transfer(new TransferRequest(1L, 2L, new BigDecimal("100.00")), "chave-1");

        ArgumentCaptor<Transfer> captor = ArgumentCaptor.forClass(Transfer.class);
        verify(transfers).save(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("chave-1");
        assertThat(captor.getValue().getRequestHash()).hasSize(64);
        assertThat(captor.getValue().getId()).isEqualTo(result.response().transferId());
        assertThat(result.replayed()).isFalse();
    }

    @Test
    void mesmaChaveDevolveATransferenciaOriginalSemMovimentarDeNovo() {
        Transfer original = previous("chave-1", new BigDecimal("100.00"));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(ana));
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(bruno));
        when(transfers.findByIdempotencyKey("chave-1")).thenReturn(Optional.of(original));

        TransferResult result = service.transfer(new TransferRequest(1L, 2L, new BigDecimal("100")), "chave-1");

        assertThat(result.replayed()).isTrue();
        assertThat(result.response().transferId()).isEqualTo(original.getId());
        assertThat(ana.getBalance()).isEqualByComparingTo("1500.00");
        assertThat(bruno.getBalance()).isEqualByComparingTo("850.00");
        verify(transfers, never()).save(any());
        verifyNoInteractions(transactions);
    }

    @Test
    void mesmaChaveComOutroValorEhRecusada() {
        Transfer original = previous("chave-1", new BigDecimal("100.00"));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(ana));
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(bruno));
        when(transfers.findByIdempotencyKey("chave-1")).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.transfer(new TransferRequest(1L, 2L, new BigDecimal("100.01")), "chave-1"))
                .isInstanceOf(IdempotencyKeyReusedException.class);

        assertThat(ana.getBalance()).isEqualByComparingTo("1500.00");
        verifyNoInteractions(transactions);
    }

    @Test
    void chaveInvalidaEhRecusadaAntesDeTravarAsContas() {
        assertThatThrownBy(() -> service.transfer(new TransferRequest(1L, 2L, BigDecimal.TEN), "chave com espaço"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.transfer(new TransferRequest(1L, 2L, BigDecimal.TEN), "x".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(repository);
    }

    private static Transfer previous(String key, BigDecimal amount) {
        String hash = IdempotencyKey.fingerprint(TransferChannel.TRANSFERENCIA, 1L, 2L, amount, null);
        return new Transfer(TransferChannel.TRANSFERENCIA, 1L, 2L, amount, null, Instant.now(), key, hash);
    }

    private static Account account(Long id, String balance) {
        return TestAccounts.account(id, balance);
    }
}
