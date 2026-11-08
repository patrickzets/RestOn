// Lista dos módulos do sistema. O menu lateral e as rotas usam esta mesma lista.

import { Perfil } from './servicos/auth.service';

export type NomeGrupo = 'Operação' | 'Financeiro' | 'Gestão';

export interface Modulo {
  caminho: string;
  titulo: string;
  grupo: NomeGrupo;
  icone: string; // id do ícone em public/icones.svg
  epico: string; // épico do Jira em que o módulo é construído
  descricao: string;
  perfis: Perfil[]; // quem pode abrir o módulo (matriz de permissões da proposta técnica)
}

export const GRUPOS: { nome: NomeGrupo; cor: string }[] = [
  { nome: 'Operação', cor: 'var(--acc)' },
  { nome: 'Financeiro', cor: 'var(--ok)' },
  { nome: 'Gestão', cor: '#6B6F7A' },
];

const GESTAO: Perfil[] = ['ADMIN', 'GERENTE'];

export const MODULOS: Modulo[] = [
  { caminho: 'visao-geral', titulo: 'Visão geral', grupo: 'Operação', icone: 'i-home', epico: 'EP09', descricao: 'Indicadores do dia com dados reais do banco.', perfis: GESTAO },
  { caminho: 'mesas', titulo: 'Mesas', grupo: 'Operação', icone: 'i-mesas', epico: 'EP03', descricao: 'Lista de mesas, estados e liberação.', perfis: ['ADMIN', 'GERENTE', 'GARCOM'] },
  { caminho: 'reservas', titulo: 'Reservas', grupo: 'Operação', icone: 'i-cal', epico: 'EP03', descricao: 'Reservas sem conflito de horário nem de capacidade.', perfis: ['ADMIN', 'GERENTE', 'GARCOM'] },
  { caminho: 'comandas', titulo: 'Comandas', grupo: 'Operação', icone: 'i-rec', epico: 'EP05', descricao: 'Comandas independentes por mesa, pedidos e fechamento individual.', perfis: ['ADMIN', 'GERENTE', 'GARCOM', 'CAIXA'] },
  { caminho: 'cozinha', titulo: 'Cozinha', grupo: 'Operação', icone: 'i-chef', epico: 'EP06', descricao: 'Quadro de pedidos por etapa, atualizado em tempo real (o bartender vê só o bar).', perfis: ['ADMIN', 'GERENTE', 'COZINHA', 'BARTENDER'] },
  { caminho: 'caixa', titulo: 'Caixa', grupo: 'Financeiro', icone: 'i-cart', epico: 'EP07', descricao: 'Pagamentos simulados, troco, estornos e turno do caixa.', perfis: ['ADMIN', 'GERENTE', 'CAIXA'] },
  { caminho: 'relatorios', titulo: 'Relatórios', grupo: 'Financeiro', icone: 'i-graf', epico: 'EP09', descricao: 'Faturamento, ticket médio e produtos mais vendidos.', perfis: GESTAO },
  { caminho: 'cardapio', titulo: 'Cardápio', grupo: 'Gestão', icone: 'i-livro', epico: 'EP04', descricao: 'Produtos, categorias, fotos e descrições.', perfis: GESTAO },
  { caminho: 'estoque', titulo: 'Estoque', grupo: 'Gestão', icone: 'i-pacote', epico: 'EP08', descricao: 'Bebidas prontas, entradas, ajustes e alerta de estoque mínimo.', perfis: GESTAO },
  { caminho: 'equipe', titulo: 'Equipe', grupo: 'Gestão', icone: 'i-gente', epico: 'EP02', descricao: 'Funcionários, perfis e permissões.', perfis: GESTAO },
  { caminho: 'auditoria', titulo: 'Auditoria', grupo: 'Gestão', icone: 'i-ok', epico: 'EP02', descricao: 'Quem fez o quê e quando.', perfis: ['ADMIN'] },
];

export function modulosDoPerfil(perfil: Perfil): Modulo[] {
  return MODULOS.filter((modulo) => modulo.perfis.includes(perfil));
}
