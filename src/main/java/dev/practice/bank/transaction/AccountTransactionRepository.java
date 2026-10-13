package dev.practice.bank.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {

    /** Intervalo semiaberto: occurredAt >= from e occurredAt < to. */
    Page<AccountTransaction> findByAccountIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            Long accountId, Instant from, Instant to, Pageable pageable);

    List<AccountTransaction> findByAccountIdOrderByIdAsc(Long accountId);
}
