-- Reservas: não comparecimento e anonimização dos dados pessoais depois do prazo de guarda (LGPD).
ALTER TABLE reserva DROP CONSTRAINT reserva_status_check;
ALTER TABLE reserva ADD CONSTRAINT reserva_status_check
    CHECK (status IN ('ATIVA', 'CHEGOU', 'CONCLUIDA', 'CANCELADA', 'NAO_COMPARECEU'));

-- Depois de anonimizada, a reserva fica sem telefone
ALTER TABLE reserva ALTER COLUMN telefone DROP NOT NULL;
ALTER TABLE reserva ADD COLUMN anonimizado_em TIMESTAMPTZ;

INSERT INTO configuracao (chave, valor, descricao) VALUES
    ('lgpd_retencao_reservas_dias', '90', 'Dias que nome e telefone de uma reserva encerrada ficam guardados antes de serem anonimizados');
