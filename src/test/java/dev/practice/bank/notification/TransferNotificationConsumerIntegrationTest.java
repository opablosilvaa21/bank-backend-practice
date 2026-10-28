package dev.practice.bank.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.practice.bank.messaging.KafkaTestReader;
import dev.practice.bank.messaging.MessagingTest;
import dev.practice.bank.messaging.OutboxEvent;
import dev.practice.bank.messaging.OutboxRepository;
import dev.practice.bank.messaging.Topics;
import dev.practice.bank.messaging.TransferCompletedMessage;
import dev.practice.bank.transfer.TransferChannel;
import dev.practice.bank.transfer.TransferCompletedEvent;
import dev.practice.bank.transfer.TransferRequest;
import dev.practice.bank.transfer.TransferService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Caminho completo: transferência -> outbox -> Kafka -> consumidor -> notificações, e o que acontece nos erros. */
@MessagingTest
class TransferNotificationConsumerIntegrationTest {

    @Autowired TransferService transfers;
    @Autowired NotificationRepository notifications;
    @Autowired OutboxRepository outbox;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired EmbeddedKafkaBroker broker;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test
    void transferenciaViraNotificacaoPeloKafka() {
        UUID id = transfers.transfer(new TransferRequest(1L, 3L, new BigDecimal("7.77"))).transferId();

        awaitNotifications(id, 2);
        assertThat(notifications.findByTransferIdOrderByIdAsc(id))
                .extracting(Notification::getMessage)
                .containsExactly("Você enviou R$ 7,77 para Carla Souza por transferência",
                        "Você recebeu R$ 7,77 de Ana Costa por transferência");
        assertThat(processedCount(outboxEventOf(id).getEventId())).isEqualTo(1);
    }

    @Test
    void mensagemRepetidaEhIgnorada() throws Exception {
        UUID id = transfers.transfer(new TransferRequest(2L, 1L, new BigDecimal("3.00"))).transferId();
        awaitNotifications(id, 2);

        // Simula a entrega repetida do relay (at-least-once): a mesma mensagem, com o mesmo eventId, de novo.
        OutboxEvent event = outboxEventOf(id);
        kafka.send(Topics.TRANSFERS, event.getMessageKey(), event.getPayload()).get(10, TimeUnit.SECONDS);

        // Uma transferência depois da repetição. O tópico tem uma partição só nos testes, então quando
        // esta for processada, a repetição (que veio antes) com certeza também já foi.
        UUID marker = transfers.transfer(new TransferRequest(3L, 2L, BigDecimal.ONE)).transferId();
        awaitNotifications(marker, 2);

        assertThat(notifications.findByTransferIdOrderByIdAsc(id)).hasSize(2);
        assertThat(processedCount(event.getEventId())).isEqualTo(1);
    }

    @Test
    void jsonInvalidoVaiDiretoParaADlt() throws Exception {
        String poison = "isto não é json " + UUID.randomUUID();
        kafka.send(Topics.TRANSFERS, "1", poison).get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, String> dead = KafkaTestReader.awaitRecord(broker, Topics.TRANSFERS_DLT,
                r -> poison.equals(r.value()));
        assertThat(allHeaders(dead)).contains("InvalidMessageException");
    }

    @Test
    void versaoDesconhecidaVaiParaADlt() throws Exception {
        ObjectNode message = (ObjectNode) json.readTree(validMessage(UUID.randomUUID(), 1L, 2L));
        message.put("version", 99);
        String payload = json.writeValueAsString(message);
        kafka.send(Topics.TRANSFERS, "1", payload).get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, String> dead = KafkaTestReader.awaitRecord(broker, Topics.TRANSFERS_DLT,
                r -> payload.equals(r.value()));
        assertThat(allHeaders(dead)).contains("Versão não suportada: 99");
    }

    @Test
    void erroAoProcessarTentaDeNovoEDepoisVaiParaADltSemDeixarNadaPelaMetade() throws Exception {
        // Mensagem bem formada, mas para uma conta que não existe: a notificação viola a chave estrangeira.
        // Não é InvalidMessageException, então o consumidor tenta de novo antes de desistir.
        UUID eventId = UUID.randomUUID();
        String payload = validMessage(eventId, 1L, 999_999L);
        kafka.send(Topics.TRANSFERS, "1", payload).get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, String> dead = KafkaTestReader.awaitRecord(broker, Topics.TRANSFERS_DLT,
                r -> payload.equals(r.value()));
        assertThat(allHeaders(dead)).contains("DataIntegrityViolationException");
        // Rollback: a marcação de "processada" foi desfeita junto com as notificações.
        assertThat(processedCount(eventId)).isZero();
    }

    private String validMessage(UUID eventId, Long from, Long to) throws Exception {
        var event = new TransferCompletedEvent(UUID.randomUUID(), TransferChannel.PIX, from, "Ana Costa", to,
                "Ninguém", new BigDecimal("1.00"), "BRL", Instant.now());
        return json.writeValueAsString(TransferCompletedMessage.of(eventId, event, Instant.now()));
    }

    private void awaitNotifications(UUID transferId, int expected) {
        await().atMost(Duration.ofSeconds(15))
                .until(() -> notifications.findByTransferIdOrderByIdAsc(transferId).size() == expected);
    }

    private OutboxEvent outboxEventOf(UUID transferId) {
        return outbox.findByAggregateTypeAndAggregateId("Transfer", transferId.toString()).orElseThrow();
    }

    private int processedCount(UUID eventId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?",
                Integer.class, eventId);
        return count == null ? 0 : count;
    }

    /** Os cabeçalhos que o DeadLetterPublishingRecoverer acrescenta trazem a classe e a mensagem do erro. */
    private static String allHeaders(ConsumerRecord<String, String> record) {
        List<String> values = StreamSupport.stream(record.headers().spliterator(), false)
                .map(h -> h.key() + "=" + new String(h.value(), StandardCharsets.UTF_8))
                .toList();
        return String.join("\n", values);
    }
}
