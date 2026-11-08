-- Comandas (uma por cliente sentado na mesa), pedidos (um por destino de preparo) e itens.
CREATE SEQUENCE comanda_codigo_seq START 1;

CREATE TABLE comanda (
    id              BIGSERIAL     PRIMARY KEY,
    codigo          VARCHAR(12)   NOT NULL UNIQUE,
    atendimento_id  BIGINT        NOT NULL REFERENCES atendimento (id),
    numero_cliente  INTEGER       NOT NULL CHECK (numero_cliente > 0),
    nome_cliente    VARCHAR(60),
    status          VARCHAR(15)   NOT NULL DEFAULT 'ABERTA' CHECK (status IN ('ABERTA', 'FECHADA', 'CANCELADA')),
    garcom_id       BIGINT        NOT NULL REFERENCES funcionario (id),
    -- A taxa vale a que estava configurada quando a comanda abriu; mudar depois não altera comandas antigas
    taxa_percentual NUMERIC(5,2)  NOT NULL,
    taxa_removida   BOOLEAN       NOT NULL DEFAULT FALSE,
    aberta_em       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fechada_em      TIMESTAMPTZ,
    version         BIGINT        NOT NULL DEFAULT 0,
    -- "Cliente 1, 2, 3" nunca se repete dentro do mesmo atendimento
    CONSTRAINT comanda_cliente_unico UNIQUE (atendimento_id, numero_cliente)
);
CREATE INDEX idx_comanda_status ON comanda (status);

CREATE TABLE pedido (
    id          BIGSERIAL   PRIMARY KEY,
    comanda_id  BIGINT      NOT NULL REFERENCES comanda (id),
    destino     VARCHAR(15) NOT NULL CHECK (destino IN ('COZINHA', 'BAR', 'SEM_PREPARO')),
    status      VARCHAR(15) NOT NULL CHECK (status IN ('RECEBIDO', 'EM_PREPARO', 'PRONTO', 'ENTREGUE', 'CANCELADO')),
    criado_por  BIGINT      NOT NULL REFERENCES funcionario (id),
    criado_em   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_pedido_status ON pedido (status);

CREATE TABLE item_pedido (
    id                   BIGSERIAL     PRIMARY KEY,
    pedido_id            BIGINT        NOT NULL REFERENCES pedido (id),
    -- Em qual comanda o item é cobrado (muda quando o item é transferido para outra comanda)
    comanda_id           BIGINT        NOT NULL REFERENCES comanda (id),
    produto_id           BIGINT        NOT NULL REFERENCES produto (id),
    quantidade           INTEGER       NOT NULL CHECK (quantidade BETWEEN 1 AND 50),
    preco_unitario       NUMERIC(12,2) NOT NULL CHECK (preco_unitario > 0),
    observacao           VARCHAR(80),
    cancelado            BOOLEAN       NOT NULL DEFAULT FALSE,
    motivo_cancelamento  VARCHAR(120)
);
CREATE INDEX idx_item_pedido_pedido ON item_pedido (pedido_id);
CREATE INDEX idx_item_pedido_comanda ON item_pedido (comanda_id);

CREATE TABLE pedido_historico (
    id               BIGSERIAL   PRIMARY KEY,
    pedido_id        BIGINT      NOT NULL REFERENCES pedido (id),
    status_anterior  VARCHAR(15),
    status_novo      VARCHAR(15) NOT NULL,
    funcionario_id   BIGINT      NOT NULL REFERENCES funcionario (id),
    em               TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pedido_historico_pedido ON pedido_historico (pedido_id);
