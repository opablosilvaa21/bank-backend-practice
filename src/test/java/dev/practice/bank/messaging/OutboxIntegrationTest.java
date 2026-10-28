package dev.practice.bank.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.practice.bank.transfer.TransferRequest;
import dev.practice.bank.transfer.TransferService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

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

        ConsumerRecord<String, String> record = readFromTopic(event.getEventId());
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

    /** Lê o tópico desde o início até achar a mensagem com este eventId no cabeçalho. */
    private ConsumerRecord<String, String> readFromTopic(UUID eventId) {
        var props = KafkaTestUtils.consumerProps("outbox-test-" + UUID.randomUUID(), "false", broker);
        props.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of(Topics.TRANSFERS));
            return await().atMost(Duration.ofSeconds(10)).until(() ->
                    StreamSupport.stream(consumer.poll(Duration.ofMillis(200)).spliterator(), false)
                            .filter(r -> eventId.toString().equals(header(r, "eventId")))
                            .findFirst(), Optional::isPresent).orElseThrow();
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
