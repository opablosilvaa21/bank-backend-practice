package dev.practice.bank.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.practice.bank.transfer.TransferCompletedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Grava o evento de transferência no outbox, DENTRO da transação da transferência.
 *
 * Por que não publicar direto no Kafka? Porque banco e Kafka não fazem commit juntos:
 * - publicar antes do commit e o commit falhar = evento de uma transferência que não existiu;
 * - publicar depois do commit e a aplicação cair = transferência sem evento.
 * Gravando no outbox, a transferência e o evento são confirmados (ou desfeitos) no mesmo commit.
 */
@Component
@ConditionalOnProperty(name = "bank.messaging.enabled", havingValue = "true")
public class TransferOutboxWriter {

    static final String AGGREGATE_TYPE = "Transfer";

    private final OutboxRepository outbox;
    private final ObjectMapper json;

    public TransferOutboxWriter(OutboxRepository outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    /** @EventListener comum (não o transacional): roda na hora, na thread e na transação de quem publicou. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(TransferCompletedEvent event) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        String payload = toJson(TransferCompletedMessage.of(eventId, event, now));
        // Chave = conta de origem: o Kafka entrega em ordem os eventos de uma mesma conta (mesma partição).
        outbox.save(new OutboxEvent(eventId, AGGREGATE_TYPE, event.transferId().toString(),
                TransferCompletedMessage.TYPE, Topics.TRANSFERS, event.fromAccountId().toString(), payload, now));
    }

    private String toJson(TransferCompletedMessage message) {
        try {
            return json.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            // Falhar aqui desfaz a transferência: melhor do que movimentar dinheiro sem evento.
            throw new IllegalStateException("Não foi possível serializar o evento da transferência", e);
        }
    }
}
