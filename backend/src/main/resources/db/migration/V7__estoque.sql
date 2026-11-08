-- Estoque por produto (só para quem tem controla_estoque) e histórico de movimentações.
ALTER TABLE produto
    ADD COLUMN controla_estoque BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN estoque_atual    INTEGER NOT NULL DEFAULT 0 CHECK (estoque_atual >= 0),
    ADD COLUMN estoque_minimo   INTEGER NOT NULL DEFAULT 0 CHECK (estoque_minimo >= 0);

CREATE TABLE movimentacao_estoque (
    id             BIGSERIAL    PRIMARY KEY,
    produto_id     BIGINT       NOT NULL REFERENCES produto (id),
    tipo           VARCHAR(20)  NOT NULL CHECK (tipo IN ('ENTRADA', 'AJUSTE', 'SAIDA_PEDIDO', 'DEVOLUCAO')),
    variacao       INTEGER      NOT NULL CHECK (variacao <> 0),
    saldo_apos     INTEGER      NOT NULL CHECK (saldo_apos >= 0),
    motivo         VARCHAR(120),
    funcionario_id BIGINT       REFERENCES funcionario (id),
    criado_em      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_movimentacao_produto ON movimentacao_estoque (produto_id, criado_em DESC);
