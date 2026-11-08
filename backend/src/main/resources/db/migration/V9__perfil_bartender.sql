-- Novo perfil: bartender (vê e prepara só os pedidos do bar)
ALTER TABLE funcionario DROP CONSTRAINT ck_funcionario_perfil;
ALTER TABLE funcionario ADD CONSTRAINT ck_funcionario_perfil
    CHECK (perfil IN ('ADMIN', 'GERENTE', 'GARCOM', 'CAIXA', 'COZINHA', 'BARTENDER'));
