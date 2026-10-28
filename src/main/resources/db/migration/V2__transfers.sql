-- Um registro por transferência (TED interna ou Pix). Os dois lançamentos do extrato apontam para ele.
CREATE TABLE transfers (
    id              UUID PRIMARY KEY,
    channel         VARCHAR(20)              NOT NULL,
    from_account_id BIGINT                   NOT NULL,
    to_account_id   BIGINT                   NOT NULL,
    amount          NUMERIC(19, 2)           NOT NULL,
    description     VARCHAR(140),
    executed_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    -- Cabeçalho Idempotency-Key enviado pelo cliente (opcional). NULL não conflita com NULL no UNIQUE.
    idempotency_key VARCHAR(64),
    -- SHA-256 dos dados do pedido: a mesma chave com outro pedido é recusada.
    request_hash    VARCHAR(64),
    CONSTRAINT fk_transfers_from_account FOREIGN KEY (from_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transfers_to_account FOREIGN KEY (to_account_id) REFERENCES accounts (id),
    CONSTRAINT uk_transfers_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_transfers_channel CHECK (channel IN ('TRANSFERENCIA', 'PIX')),
    CONSTRAINT ck_transfers_amount CHECK (amount > 0),
    CONSTRAINT ck_transfers_distinct_accounts CHECK (from_account_id <> to_account_id)
);

CREATE INDEX ix_transfers_from_account ON transfers (from_account_id);
CREATE INDEX ix_transfers_to_account ON transfers (to_account_id);

-- Transferências feitas antes desta migration só existem como pares de lançamentos no extrato.
-- Reconstrói cada uma a partir do lançamento de saída e do de entrada, para a chave estrangeira abaixo valer.
-- A descrição original não é recuperável (ficou misturada ao texto do extrato), então fica NULL.
INSERT INTO transfers (id, channel, from_account_id, to_account_id, amount, description, executed_at)
SELECT sent.transfer_id,
       CASE WHEN sent.type = 'PIX_ENVIADO' THEN 'PIX' ELSE 'TRANSFERENCIA' END,
       sent.account_id,
       received.account_id,
       sent.amount,
       NULL,
       sent.occurred_at
FROM account_transactions sent
JOIN account_transactions received
  ON received.transfer_id = sent.transfer_id AND received.id <> sent.id
WHERE sent.type IN ('TRANSFERENCIA_ENVIADA', 'PIX_ENVIADO');

ALTER TABLE account_transactions
    ADD CONSTRAINT fk_account_transactions_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (id);
