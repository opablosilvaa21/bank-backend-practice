package dev.practice.bank.notification;

import dev.practice.bank.account.AccountNotFoundException;
import dev.practice.bank.account.AccountRepository;
import dev.practice.bank.transfer.TransferChannel;
import dev.practice.bank.transfer.TransferCompletedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class NotificationService {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final NotificationRepository notifications;
    private final AccountRepository accounts;

    public NotificationService(NotificationRepository notifications, AccountRepository accounts) {
        this.notifications = notifications;
        this.accounts = accounts;
    }

    /** Um aviso para quem enviou e outro para quem recebeu. */
    @Transactional
    public void notifyTransfer(TransferCompletedEvent event) {
        String amount = brl(event.amount());
        String via = event.channel() == TransferChannel.PIX ? "por Pix" : "por transferência";
        Instant now = Instant.now();
        notifications.saveAll(List.of(
                new Notification(event.fromAccountId(), event.transferId(),
                        "Você enviou %s para %s %s".formatted(amount, event.toOwnerName(), via), now),
                new Notification(event.toAccountId(), event.transferId(),
                        "Você recebeu %s de %s %s".formatted(amount, event.fromOwnerName(), via), now)));
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(Long accountId, Pageable pageable) {
        if (!accounts.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return notifications.findByAccountIdOrderByCreatedAtDescIdDesc(accountId, pageable)
                .map(NotificationResponse::from);
    }

    /** 1250.5 vira "R$ 1.250,50". */
    private static String brl(BigDecimal amount) {
        return String.format(PT_BR, "R$ %,.2f", amount);
    }
}
