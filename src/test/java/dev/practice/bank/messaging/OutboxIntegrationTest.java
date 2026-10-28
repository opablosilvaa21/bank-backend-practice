package dev.practice.bank.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.practice.bank.transfer.TransferRequest;
import dev.practice.bank.transfer.TransferService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@MessagingTest
class OutboxIntegrationTest {

    @Autowired TransferService transfers;
    @Autowired OutboxRepository outbox;
    @Autowired TransactionTemplate tx;
    @Autowired EmbeddedKafkaBroker broker;
    @Autowired ObjectMapper json;

    @Test
    void oEventoEhGravadoNaMesmaTransacaoDaTransferencia() {
        // Dentro da transação o evento já está no outbox; com o rollback, some junto com a transferência.
        UUID id = tx.execute(status -> {
            UUID transferId = transfers.transfer(new TransferRequest(1L, 2L, BigDecimal.ONE)).transferId();
            assertThat(find(transferId)).isPresent();
            status.setRollbackOnly();
            return transferId;
        });

        assertThat(find(id)).isEmpty();
    }

    @Test
    void oRelayPublicaNoKafkaEMarcaComoPublicado() throws Exception {
        UUID id = transfers.transfer(new TransferRequest(3L, 2L, new BigDecimal("12.34"), "Almoço")).transferId();

        OutboxEvent event = await().atMost(Duration.ofSeconds(10))
                .until(() -> find(id).filter(e -> e.getPublishedAt() != null), Optional::isPresent)
                .orElseThrow();
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getMessageKey()).isEqualTo("3");

        ConsumerRecord<String, String> record = KafkaTestReader.awaitRecord(broker, Topics.TRANSFERS,
                r -> event.getEventId().toString().equals(KafkaTestReader.header(r, "eventId")));
        assertThat(record.key()).isEqualTo("3");
        JsonNode message = json.readTree(record.value());
        assertThat(message.get("eventType").asText()).isEqualTo("TransferCompleted");
        assertThat(message.get("version").asInt()).isEqualTo(1);
        assertThat(message.get("transferId").asText()).isEqualTo(id.toString());
        assertThat(message.get("fromOwnerName").asText()).isEqualTo("Carla Souza");
        assertThat(message.get("amount").decimalValue()).isEqualByComparingTo("12.34");
    }

    private Optional<OutboxEvent> find(UUID transferId) {
        return outbox.findByAggregateTypeAndAggregateId(TransferOutboxWriter.AGGREGATE_TYPE, transferId.toString());
    }
}
