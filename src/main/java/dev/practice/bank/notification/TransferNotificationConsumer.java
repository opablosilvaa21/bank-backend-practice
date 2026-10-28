package dev.practice.bank.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.practice.bank.messaging.Topics;
import dev.practice.bank.messaging.TransferCompletedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria as notificações a partir das mensagens do Kafka (bank.transfers). Funciona como um serviço à parte:
 * só conhece a mensagem, nunca as tabelas de transferência.
 *
 * Idempotente: a marcação em processed_events e as notificações são gravadas na mesma transação.
 * Mensagem repetida é ignorada; se algo falhar no meio, as duas coisas são desfeitas e a mensagem
 * é tentada de novo (e, esgotadas as tentativas, vai para a DLT: veja MessagingConfig).
 */
@Component
@ConditionalOnProperty(name = "bank.messaging.enabled", havingValue = "true")
public class TransferNotificationConsumer {

    static final String CONSUMER = "notifications";
    private static final Logger log = LoggerFactory.getLogger(TransferNotificationConsumer.class);

    private final NotificationService notifications;
    private final ProcessedEvents processed;
    private final ObjectMapper json;

    public TransferNotificationConsumer(NotificationService notifications, ProcessedEvents processed,
                                        ObjectMapper json) {
        this.notifications = notifications;
        this.processed = processed;
        this.json = json;
    }

    @KafkaListener(topics = Topics.TRANSFERS)
    @Transactional
    public void on(String payload) {
        TransferCompletedMessage message = TransferCompletedMessage.parse(json, payload);
        if (!processed.markProcessed(CONSUMER, message.eventId())) {
            log.info("Mensagem {} repetida: já processada, ignorando", message.eventId());
            return;
        }
        notifications.notifyTransfer(message.toEvent());
        log.info("Notificações criadas para a transferência {}", message.transferId());
    }
}
