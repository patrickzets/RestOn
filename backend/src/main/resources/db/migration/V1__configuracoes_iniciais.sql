-- Configurações do restaurante (o Administrador altera pela tela de configurações).
-- Cada comanda guarda a taxa que valia quando foi aberta; mudar aqui não altera comandas antigas.
CREATE TABLE configuracao (
    chave         VARCHAR(60)  PRIMARY KEY,
    valor         VARCHAR(200) NOT NULL,
    descricao     VARCHAR(200),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO configuracao (chave, valor, descricao) VALUES
    ('taxa_servico_percentual', '10.00', 'Taxa de serviço aplicada nas comandas novas, em %'),
    ('reserva_duracao_min',     '120',   'Duração padrão de uma reserva, em minutos'),
    ('reserva_tolerancia_min',  '15',    'Tolerância de atraso de uma reserva, em minutos');
