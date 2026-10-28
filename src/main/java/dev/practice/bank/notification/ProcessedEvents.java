package dev.practice.bank.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** Registro das mensagens já processadas por cada consumidor (tabela processed_events). */
@Repository
public class ProcessedEvents {

    private final JdbcTemplate jdbc;

    public ProcessedEvents(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Marca a mensagem como processada. Devolve false se ela já estava marcada (é uma repetição).
     * Um INSERT só, sem SELECT antes: se duas cópias chegarem juntas, a chave primária garante que só uma passa.
     * Deve rodar na mesma transação do efeito da mensagem: se o efeito falhar, a marcação é desfeita junto.
     */
    public boolean markProcessed(String consumer, UUID eventId) {
        int inserted = jdbc.update("""
                INSERT INTO processed_events (consumer, event_id, processed_at) VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """, consumer, eventId, Timestamp.from(Instant.now()));
        return inserted == 1;
    }
}
