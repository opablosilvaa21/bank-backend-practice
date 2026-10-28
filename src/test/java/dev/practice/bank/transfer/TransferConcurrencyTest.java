package dev.practice.bank.transfer;

import dev.practice.bank.account.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dispara transferências em paralelo para provar que o lock pessimista
 * impede saldo negativo, "lost update" e deadlock.
 * Sem @Transactional no teste: cada transferência precisa fazer commit de verdade.
 * Roda num schema próprio ("concurrency") para os commits não afetarem os outros testes.
 * O @DirtiesContext recria o contexto, e o TestDatabaseConfig zera o schema antes de cada teste.
 */
@SpringBootTest(properties = {
        "spring.flyway.schemas=concurrency",
        "spring.datasource.hikari.schema=concurrency"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TransferConcurrencyTest {

    @Autowired
    TransferService service;

    @Autowired
    AccountRepository repository;

    @Test
    void transferenciasSimultaneasNuncaDeixamSaldoNegativo() throws Exception {
        // Conta 1 tem R$ 1.500,00: só 15 de 20 transferências de R$ 100 podem passar.
        int attempts = 20;
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        runInParallel(attempts, i -> {
            try {
                service.transfer(new TransferRequest(1L, 2L, new BigDecimal("100.00")));
                succeeded.incrementAndGet();
            } catch (InsufficientBalanceException e) {
                rejected.incrementAndGet();
            }
        });

        assertThat(succeeded.get()).isEqualTo(15);
        assertThat(rejected.get()).isEqualTo(5);
        assertThat(balance(1L)).isEqualByComparingTo("0.00");
        assertThat(balance(2L)).isEqualByComparingTo("2350.00");
    }

    @Test
    void transferenciasCruzadasNaoCausamDeadlockENemPerdemDinheiro() throws Exception {
        BigDecimal totalBefore = balance(2L).add(balance(3L));

        // Metade vai de 2 para 3, metade de 3 para 2, ao mesmo tempo.
        runInParallel(40, i -> {
            if (i % 2 == 0) {
                service.transfer(new TransferRequest(2L, 3L, new BigDecimal("10.00")));
            } else {
                service.transfer(new TransferRequest(3L, 2L, new BigDecimal("10.00")));
            }
        });

        assertThat(balance(2L).add(balance(3L))).isEqualByComparingTo(totalBefore);
        assertThat(balance(2L)).isEqualByComparingTo("850.00");
        assertThat(balance(3L)).isEqualByComparingTo("3200.00");
    }

    @Test
    void mesmaIdempotencyKeyEmParaleloTransfereUmaVezSo() throws Exception {
        // Cliente impaciente: o mesmo pedido chega 10 vezes ao mesmo tempo.
        Set<UUID> transferIds = ConcurrentHashMap.newKeySet();
        AtomicInteger replayed = new AtomicInteger();

        runInParallel(10, i -> {
            TransferResult result = service.transfer(new TransferRequest(1L, 2L, new BigDecimal("100.00")), "pedido-unico");
            transferIds.add(result.response().transferId());
            if (result.replayed()) {
                replayed.incrementAndGet();
            }
        });

        assertThat(transferIds).hasSize(1);
        assertThat(replayed.get()).isEqualTo(9);
        assertThat(balance(1L)).isEqualByComparingTo("1400.00");
        assertThat(balance(2L)).isEqualByComparingTo("950.00");
    }

    private BigDecimal balance(Long id) {
        return repository.findById(id).orElseThrow().getBalance();
    }

    private interface Task {
        void run(int index) throws Exception;
    }

    /** Libera todas as threads juntas e propaga qualquer erro inesperado. */
    private static void runInParallel(int count, Task task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    task.run(index);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
