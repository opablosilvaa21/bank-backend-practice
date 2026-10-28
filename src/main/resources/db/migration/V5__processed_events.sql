-- Mensagens que cada consumidor já processou. O Kafka (com o outbox) entrega "pelo menos uma vez":
-- a mesma mensagem pode chegar de novo, e esta tabela faz o consumidor ignorar a repetição.
CREATE TABLE processed_events (
    consumer     VARCHAR(100)             NOT NULL,
    event_id     UUID                     NOT NULL,
    processed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (consumer, event_id)
);
