-- Caixa: turnos, pagamentos (simulados) e estornos. Nada aqui é apagado: estorno é um registro novo.
CREATE TABLE caixa_turno (
    id             BIGSERIAL     PRIMARY KEY,
    operador_id    BIGINT        NOT NULL REFERENCES funcionario (id),
    aberto_em      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fechado_em     TIMESTAMPTZ,
    valor_inicial  NUMERIC(12,2) NOT NULL CHECK (valor_inicial >= 0),
    valor_contado  NUMERIC(12,2) CHECK (valor_contado >= 0),
    status         VARCHAR(10)   NOT NULL DEFAULT 'ABERTO' CHECK (status IN ('ABERTO', 'FECHADO')),
    version        BIGINT        NOT NULL DEFAULT 0
);
-- Só pode existir um turno aberto por vez (o banco recusa o segundo, mesmo que o código falhe)
CREATE UNIQUE INDEX caixa_um_turno_aberto ON caixa_turno (status) WHERE status = 'ABERTO';

CREATE TABLE pagamento (
    id              BIGSERIAL     PRIMARY KEY,
    -- A comanda pode ser vazia enquanto o pagamento é antecipado de uma reserva (vira crédito na primeira comanda)
    comanda_id      BIGINT        REFERENCES comanda (id),
    reserva_id      BIGINT        REFERENCES reserva (id),
    turno_id        BIGINT        REFERENCES caixa_turno (id),
    metodo          VARCHAR(10)   NOT NULL CHECK (metodo IN ('PIX', 'CARTAO', 'DINHEIRO')),
    origem          VARCHAR(10)   NOT NULL CHECK (origem IN ('CAIXA', 'RESERVA')),
    valor           NUMERIC(12,2) NOT NULL CHECK (valor > 0),
    valor_recebido  NUMERIC(12,2) NOT NULL CHECK (valor_recebido >= valor),
    troco           NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (troco >= 0),
    status          VARCHAR(12)   NOT NULL DEFAULT 'CONFIRMADO' CHECK (status IN ('CONFIRMADO', 'ESTORNADO')),
    criado_por      BIGINT        NOT NULL REFERENCES funcionario (id),
    criado_em       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version         BIGINT        NOT NULL DEFAULT 0,
    CHECK (comanda_id IS NOT NULL OR reserva_id IS NOT NULL),
    -- Troco só existe em dinheiro
    CHECK (metodo = 'DINHEIRO' OR troco = 0)
);
CREATE INDEX idx_pagamento_comanda ON pagamento (comanda_id);
CREATE INDEX idx_pagamento_reserva ON pagamento (reserva_id);
CREATE INDEX idx_pagamento_turno ON pagamento (turno_id);

CREATE TABLE estorno (
    id               BIGSERIAL     PRIMARY KEY,
    pagamento_id     BIGINT        NOT NULL REFERENCES pagamento (id),
    valor            NUMERIC(12,2) NOT NULL CHECK (valor > 0),
    motivo           VARCHAR(120)  NOT NULL,
    autorizado_por   BIGINT        NOT NULL REFERENCES funcionario (id),
    em               TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_estorno_pagamento ON estorno (pagamento_id);

-- Totais gravados no fechamento da comanda: depois de fechada, o valor nunca mais muda
ALTER TABLE comanda
    ADD COLUMN subtotal_final   NUMERIC(12,2),
    ADD COLUMN taxa_valor_final NUMERIC(12,2),
    ADD COLUMN total_final      NUMERIC(12,2);
