package dev.practice.bank.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Próximos eventos pendentes, na ordem em que foram gravados, já travados para este relay.
     * SKIP LOCKED: se outra instância da aplicação estiver publicando alguns deles, pula esses em vez de esperar.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextPending(@Param("limit") int limit);

    Optional<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, String aggregateId);

    long countByPublishedAtIsNull();
}
