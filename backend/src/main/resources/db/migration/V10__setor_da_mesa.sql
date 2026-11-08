-- Setor da mesa (Salão, Varanda, Balcão...): agrupa as mesas na tela sem precisar desenhar o salão
ALTER TABLE mesa ADD COLUMN setor VARCHAR(30) NOT NULL DEFAULT 'Salão';
