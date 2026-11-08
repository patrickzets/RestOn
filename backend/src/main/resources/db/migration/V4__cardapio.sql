-- Cardápio: categorias e produtos. As fotos ficam em disco; aqui só o nome do arquivo.
CREATE TABLE categoria (
    id      BIGSERIAL   PRIMARY KEY,
    nome    VARCHAR(60) NOT NULL,
    ordem   INTEGER     NOT NULL DEFAULT 0,
    version BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX categoria_nome_unico ON categoria (lower(nome));

CREATE TABLE produto (
    id                BIGSERIAL     PRIMARY KEY,
    categoria_id      BIGINT        NOT NULL REFERENCES categoria (id),
    nome              VARCHAR(100)  NOT NULL,
    descricao         VARCHAR(160),
    preco             NUMERIC(12,2) NOT NULL CHECK (preco > 0),
    imagem            VARCHAR(60),
    tempo_preparo_min INTEGER       NOT NULL DEFAULT 0 CHECK (tempo_preparo_min BETWEEN 0 AND 240),
    destino_preparo   VARCHAR(15)   NOT NULL CHECK (destino_preparo IN ('COZINHA', 'BAR', 'SEM_PREPARO')),
    disponivel        BOOLEAN       NOT NULL DEFAULT TRUE,
    ativo             BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizado_em     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version           BIGINT        NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX produto_nome_unico ON produto (lower(nome));
CREATE INDEX idx_produto_categoria ON produto (categoria_id);
