package dev.practice.bank.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByAccountIdOrderByCreatedAtDescIdDesc(Long accountId, Pageable pageable);

    List<Notification> findByTransferIdOrderByIdAsc(UUID transferId);
}
