package dev.practice.bank.messaging;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publica no Kafka os eventos pendentes do outbox.
 *
 * Entrega "pelo menos uma vez" (at-least-once): se o Kafka confirmar o envio e a aplicação cair antes de marcar
 * o evento como publicado, ele é enviado de novo na próxima rodada. Por isso o consumidor precisa ser idempotente.
 */
@Component
@ConditionalOnProperty(name = "bank.messaging.enabled", havingValue = "true")
public class OutboxRelay {

    static final int BATCH_SIZE = 100;
    private static final long SEND_TIMEOUT_SECONDS = 10;
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;

    public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka, TransactionTemplate tx) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.tx = tx;
    }

    /** fixedDelay: a próxima rodada só começa depois que a anterior termina, nunca se sobrepõem. */
    @Scheduled(fixedDelayString = "${bank.outbox.relay.interval-ms:1000}")
    public void publishPending() {
        Integer sent;
        do {
            sent = tx.execute(status -> publishBatch());
        } while (sent != null && sent == BATCH_SIZE);
    }

    private int publishBatch() {
        List<OutboxEvent> batch = outbox.lockNextPending(BATCH_SIZE);
        int sent = 0;
        for (OutboxEvent event : batch) {
            try {
                // Espera a confirmação do Kafka (acks=all) antes de marcar como publicado.
                kafka.send(toRecord(event)).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                event.markPublished(Instant.now());
                sent++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                event.markFailed(e.getClass().getSimpleName() + ": " + e.getMessage());
                log.warn("Kafka indisponível; evento {} continua no outbox (tentativa {})",
                        event.getEventId(), event.getAttempts());
                // Para no primeiro erro: publicar os mais novos antes deste quebraria a ordem.
                break;
            }
        }
        if (sent > 0) {
            log.info("Outbox: {} evento(s) publicado(s) no Kafka", sent);
        }
        return sent;
    }

    private static ProducerRecord<String, String> toRecord(OutboxEvent event) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload());
        record.headers().add("eventId", event.getEventId().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
