import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';

import { AuditoriaService, RegistroDeAuditoria } from '../../servicos/auditoria.service';

// Nomes das ações em português claro (o banco guarda o código)
const NOMES_DAS_ACOES: Record<string, string> = {
  LOGIN: 'Entrou no sistema',
  LOGOUT: 'Saiu do sistema',
  LOGIN_FALHA: 'Tentativa de login errada',
  LOGIN_NEGADO: 'Login negado (conta bloqueada ou desativada)',
  CONTA_BLOQUEADA: 'Conta bloqueada por senhas erradas',
  FUNCIONARIO_CRIADO: 'Funcionário criado',
  FUNCIONARIO_ALTERADO: 'Funcionário alterado',
  FUNCIONARIO_BLOQUEADO: 'Funcionário bloqueado',
  FUNCIONARIO_DESBLOQUEADO: 'Funcionário desbloqueado',
  FUNCIONARIO_DESATIVADO: 'Funcionário desativado',
  FUNCIONARIO_ATIVADO: 'Funcionário reativado',
  SENHA_ALTERADA: 'Trocou a própria senha',
  SENHA_REDEFINIDA: 'Senha redefinida por um gestor',
  ADMIN_INICIAL_CRIADO: 'Administrador inicial criado',
  MESA_CRIADA: 'Mesa cadastrada',
  MESA_ALTERADA: 'Mesa alterada',
  MESA_LIBERADA: 'Mesa liberada',
  MESA_TROCADA: 'Atendimento trocado de mesa',
  ATENDIMENTO_ABERTO: 'Atendimento aberto',
  ATENDIMENTO_ENCERRADO: 'Atendimento encerrado',
  RESERVA_CRIADA: 'Reserva criada',
  RESERVA_ALTERADA: 'Reserva alterada',
  RESERVA_CANCELADA: 'Reserva cancelada',
  RESERVA_CHEGOU: 'Cliente da reserva chegou',
  RESERVA_NAO_COMPARECEU: 'Reserva marcada como não compareceu',
  RESERVAS_ANONIMIZADAS: 'Dados de reservas antigas anonimizados',
  CATEGORIA_CRIADA: 'Categoria criada',
  CATEGORIA_ALTERADA: 'Categoria renomeada',
  CATEGORIA_REORDENADA: 'Categoria reordenada',
  CATEGORIA_EXCLUIDA: 'Categoria excluída',
  PRODUTO_CRIADO: 'Produto criado',
  PRODUTO_ALTERADO: 'Produto alterado',
  PRODUTO_DISPONIVEL: 'Produto marcado como disponível',
  PRODUTO_INDISPONIVEL: 'Produto marcado como indisponível',
  PRODUTO_ARQUIVADO: 'Produto arquivado',
  PRODUTO_RESTAURADO: 'Produto restaurado',
  PRODUTO_IMAGEM_ALTERADA: 'Foto do produto trocada',
  PRODUTO_IMAGEM_REMOVIDA: 'Foto do produto removida',
  COMANDA_ABERTA: 'Comanda aberta',
  COMANDA_CANCELADA: 'Comanda cancelada',
  PEDIDO_CRIADO: 'Pedido enviado',
  PEDIDO_STATUS: 'Pedido mudou de etapa',
  ITEM_CANCELADO: 'Item cancelado',
  ITEM_TRANSFERIDO: 'Item transferido de comanda',
  TURNO_ABERTO: 'Caixa aberto',
  TURNO_FECHADO: 'Caixa fechado',
  PAGAMENTO_RECEBIDO: 'Pagamento recebido',
  PAGAMENTO_ESTORNADO: 'Pagamento estornado',
  PAGAMENTO_ANTECIPADO: 'Pagamento antecipado de reserva',
  COMANDA_FECHADA: 'Comanda fechada',
  TAXA_REMOVIDA: 'Taxa de serviço removida',
  TAXA_RESTAURADA: 'Taxa de serviço devolvida',
  ESTOQUE_CONTROLE: 'Controle de estoque alterado',
  ESTOQUE_ENTRADA: 'Entrada no estoque',
  ESTOQUE_AJUSTE: 'Saldo do estoque ajustado',
};

const TAMANHO_DA_PAGINA = 20;

@Component({
  selector: 'app-auditoria',
  imports: [DatePipe],
  templateUrl: './auditoria.html',
  styleUrl: './auditoria.css',
})
export class Auditoria implements OnInit {
  private api = inject(AuditoriaService);

  registros = signal<RegistroDeAuditoria[]>([]);
  total = signal(0);
  pagina = signal(0);
  filtro = signal('');
  carregando = signal(true);
  erro = signal('');

  totalDePaginas = computed(() => Math.max(1, Math.ceil(this.total() / TAMANHO_DA_PAGINA)));

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set('');
    this.api.listar(this.pagina(), TAMANHO_DA_PAGINA, this.filtro()).subscribe({
      next: (resposta) => {
        this.registros.set(resposta.itens);
        this.total.set(resposta.total);
        this.carregando.set(false);
      },
      error: (erro: HttpErrorResponse) => {
        this.erro.set(erro.error?.detail ?? 'Não consegui carregar a auditoria.');
        this.carregando.set(false);
      },
    });
  }

  filtrar(texto: string): void {
    this.filtro.set(texto.trim());
    this.pagina.set(0);
    this.carregar();
  }

  irPara(pagina: number): void {
    if (pagina < 0 || pagina >= this.totalDePaginas()) return;
    this.pagina.set(pagina);
    this.carregar();
  }

  nomeDaAcao(codigo: string): string {
    return NOMES_DAS_ACOES[codigo] ?? codigo;
  }

  gravidade(codigo: string): 'perigo' | 'aviso' | '' {
    if (codigo === 'CONTA_BLOQUEADA' || codigo === 'LOGIN_NEGADO') return 'perigo';
    if (codigo === 'LOGIN_FALHA' || codigo === 'FUNCIONARIO_BLOQUEADO' || codigo === 'FUNCIONARIO_DESATIVADO') return 'aviso';
    return '';
  }
}
