-- Mesas, reservas e atendimentos (quem está sentado numa mesa).
-- A extensão btree_gist deixa o PostgreSQL comparar "mesma mesa" junto com "horários que se cruzam".
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE mesa (
    id         BIGSERIAL   PRIMARY KEY,
    numero     INTEGER     NOT NULL UNIQUE CHECK (numero > 0),
    capacidade INTEGER     NOT NULL CHECK (capacidade BETWEEN 1 AND 30),
    estado     VARCHAR(25) NOT NULL DEFAULT 'LIVRE'
               CHECK (estado IN ('LIVRE', 'OCUPADA', 'AGUARDANDO_LIBERACAO')),
    ativa      BOOLEAN     NOT NULL DEFAULT TRUE,
    version    BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE reserva (
    id           BIGSERIAL    PRIMARY KEY,
    mesa_id      BIGINT       NOT NULL REFERENCES mesa (id),
    nome_cliente VARCHAR(120) NOT NULL,
    telefone     VARCHAR(20)  NOT NULL,
    qtd_pessoas  INTEGER      NOT NULL CHECK (qtd_pessoas > 0),
    inicio       TIMESTAMPTZ  NOT NULL,
    fim          TIMESTAMPTZ  NOT NULL,
    status       VARCHAR(15)  NOT NULL DEFAULT 'ATIVA'
                 CHECK (status IN ('ATIVA', 'CHEGOU', 'CONCLUIDA', 'CANCELADA')),
    criado_por   BIGINT       NOT NULL REFERENCES funcionario (id),
    criado_em    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      BIGINT       NOT NULL DEFAULT 0,
    CHECK (fim > inicio),
    -- A mesma mesa não pode ter duas reservas em horários que se cruzam (vale também se o código falhar)
    CONSTRAINT reserva_sem_conflito EXCLUDE USING gist (
        mesa_id WITH =,
        tstzrange(inicio, fim) WITH &&
    ) WHERE (status IN ('ATIVA', 'CHEGOU'))
);
CREATE INDEX idx_reserva_inicio ON reserva (inicio);

CREATE TABLE atendimento (
    id          BIGSERIAL   PRIMARY KEY,
    mesa_id     BIGINT      NOT NULL REFERENCES mesa (id),
    reserva_id  BIGINT      REFERENCES reserva (id),
    aberto_por  BIGINT      NOT NULL REFERENCES funcionario (id),
    aberto_em   TIMESTAMPTZ NOT NULL DEFAULT now(),
    encerrado_em TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0
);
-- Uma mesa só pode ter um atendimento aberto por vez
CREATE UNIQUE INDEX atendimento_um_aberto_por_mesa ON atendimento (mesa_id) WHERE encerrado_em IS NULL;
