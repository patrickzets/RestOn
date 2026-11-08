-- EP02: funcionários (quem usa o sistema) e registro de auditoria.

CREATE TABLE funcionario (
    id                BIGSERIAL    PRIMARY KEY,
    nome              VARCHAR(120) NOT NULL,
    login             VARCHAR(40)  NOT NULL,
    senha_hash        VARCHAR(255) NOT NULL,
    perfil            VARCHAR(20)  NOT NULL,
    ativo             BOOLEAN      NOT NULL DEFAULT TRUE,   -- desativar = deixou a empresa
    bloqueado         BOOLEAN      NOT NULL DEFAULT FALSE,  -- bloquear = suspensão temporária
    tentativas_falha  INTEGER      NOT NULL DEFAULT 0,
    senha_provisoria  BOOLEAN      NOT NULL DEFAULT FALSE,  -- precisa trocar a senha no próximo acesso
    ultimo_acesso_em  TIMESTAMPTZ,
    criado_em         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_funcionario_perfil CHECK (perfil IN ('ADMIN', 'GERENTE', 'GARCOM', 'CAIXA', 'COZINHA'))
);

-- Login único sem diferenciar maiúscula de minúscula
CREATE UNIQUE INDEX ux_funcionario_login ON funcionario (lower(login));

-- Auditoria: quem fez o quê e quando. Os registros nunca são alterados nem apagados pelo sistema.
CREATE TABLE audit_log (
    id               BIGSERIAL    PRIMARY KEY,
    funcionario_id   BIGINT       REFERENCES funcionario (id),
    login_informado  VARCHAR(60),                    -- login de quem fez (ou tentou fazer) a ação
    acao             VARCHAR(60)  NOT NULL,
    entidade         VARCHAR(60),
    entidade_id      VARCHAR(60),
    detalhes         TEXT,
    ip               VARCHAR(45),
    em               TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_log_em ON audit_log (em DESC);
CREATE INDEX ix_audit_log_funcionario ON audit_log (funcionario_id);
