package dev.practice.bank.notification;

import dev.practice.bank.transfer.TransferCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cria as notificações quando uma transferência termina.
 *
 * - AFTER_COMMIT: só reage se o dinheiro realmente mudou de conta. Se a transferência falhar e sofrer rollback,
 *   o evento é descartado e ninguém recebe um aviso falso.
 * - @Async: roda em outra thread. Sem isso, o AFTER_COMMIT roda na thread da transferência ANTES de ela devolver
 *   a conexão ao pool, e gravar a notificação pede uma segunda conexão. Com muitas transferências ao mesmo tempo,
 *   todas seguram uma conexão e esperam outra: o pool esgota e tudo trava (o TransferConcurrencyTest pegou isso).
 *   Em outra thread, a transferência responde na hora e a notificação ganha uma transação só dela.
 * - Limite: se a aplicação cair entre o commit e este método, a notificação se perde. O outbox resolve isso.
 *
 * Só existe com a mensageria desligada. Ligada, o evento vai para o outbox e a notificação vem do Kafka.
 */
@Component
@ConditionalOnProperty(name = "bank.messaging.enabled", havingValue = "false", matchIfMissing = true)
public class TransferNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(TransferNotificationListener.class);

    private final NotificationService notifications;

    public TransferNotificationListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TransferCompletedEvent event) {
        try {
            notifications.notifyTransfer(event);
        } catch (RuntimeException e) {
            // A transferência já foi confirmada; aqui só dá para registrar a falha.
            log.error("Notificação da transferência {} não foi criada", event.transferId(), e);
        }
    }
}
