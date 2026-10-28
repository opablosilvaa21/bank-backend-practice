package dev.practice.bank.messaging;

import dev.practice.bank.account.AccountRepository;
import dev.practice.bank.transfer.TransferRequest;
import dev.practice.bank.transfer.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Kafka fora do ar (ninguém escutando na porta 1): a transferência funciona normalmente e o evento
 * espera no outbox, com as tentativas registradas, até o Kafka voltar.
 */
@SpringBootTest(properties = {
        "bank.messaging.enabled=true",
        "bank.outbox.relay.interval-ms=100",
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.producer.properties.max.block.ms=500",
        "spring.kafka.admin.operation-timeout=1s",
        "spring.flyway.schemas=kafka_down",
        "spring.datasource.hikari.schema=kafka_down"
})
class OutboxKafkaDownIntegrationTest {

    @Autowired TransferService transfers;
    @Autowired AccountRepository accounts;
    @Autowired OutboxRepository outbox;

    @Test
    void semKafkaATransferenciaFuncionaEOEventoEsperaNoOutbox() {
        UUID id = transfers.transfer(new TransferRequest(1L, 2L, new BigDecimal("100.00"))).transferId();

        // O dinheiro se moveu: a transferência não depende do Kafka.
        assertThat(accounts.findById(1L).orElseThrow().getBalance()).isEqualByComparingTo("1400.00");

        // O relay tenta, falha e registra o erro; o evento continua pendente, nada se perde.
        OutboxEvent event = await().atMost(Duration.ofSeconds(15)).until(
                () -> outbox.findByAggregateTypeAndAggregateId(TransferOutboxWriter.AGGREGATE_TYPE, id.toString())
                        .orElseThrow(),
                e -> e.getAttempts() >= 2);
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getLastError()).isNotBlank();
        assertThat(outbox.countByPublishedAtIsNull()).isEqualTo(1);
    }
}
