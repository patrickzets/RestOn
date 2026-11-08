import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, debounceTime, distinctUntilChanged, filter, map, of, switchMap } from 'rxjs';

import { GavetaAcessivel } from '../../layout/gaveta-acessivel';
import { rolarAteAGaveta } from '../../rolar';
import { AuthService } from '../../servicos/auth.service';
import { mensagemDoErro } from '../../servicos/erros';
import { CaixaService, MetodoPagamento, NOMES_DOS_METODOS } from '../../servicos/caixa.service';
import { Mesa, MesasService } from '../../servicos/mesas.service';
import { Reserva, ReservasService } from '../../servicos/reservas.service';

// Data no horário do navegador, como 2026-11-05 (toISOString usaria UTC e pode mudar o dia)
function dataLocal(data: Date): string {
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${data.getFullYear()}-${mes}-${dia}`;
}

function horaLocal(data: Date): string {
  return `${String(data.getHours()).padStart(2, '0')}:${String(data.getMinutes()).padStart(2, '0')}`;
}

@Component({
  selector: 'app-reservas',
  imports: [DatePipe, ReactiveFormsModule, GavetaAcessivel],
  templateUrl: './reservas.html',
  styleUrl: './reservas.css',
})
export class Reservas implements OnInit {
  private api = inject(ReservasService);
  private mesasApi = inject(MesasService);
  private caixaApi = inject(CaixaService);
  private auth = inject(AuthService);
  private fb = inject(NonNullableFormBuilder);

  dia = signal(dataLocal(new Date()));
  reservas = signal<Reserva[]>([]);
  carregando = signal(true);
  erroDaLista = signal('');

  painel = signal<'fechado' | 'nova' | 'editar'>('fechado');
  selecionada = signal<Reserva | null>(null);
  salvando = signal(false);
  erroDoPainel = signal('');
  confirmandoCancelar = signal(false);
  mesasLivres = signal<Mesa[]>([]);
  nomesDosMetodos = NOMES_DOS_METODOS;
  metodosAntecipados: MetodoPagamento[] = ['PIX', 'CARTAO'];
  metodoAntecipado = signal<MetodoPagamento>('PIX');
  valorAntecipado = signal<number | null>(null);
  buscandoMesas = signal(false);

  form = this.fb.group({
    nomeCliente: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    telefone: ['', [Validators.required, Validators.pattern(/^\(?\d{2}\)?\s?9?\d{4}-?\d{4}$/)]],
    qtdPessoas: [2, [Validators.required, Validators.min(1), Validators.max(30)]],
    data: ['', Validators.required],
    hora: ['', Validators.required],
    mesaId: [0, [Validators.required, Validators.min(1)]],
  });

  // Gerente e administrador veem o telefone; o garçom não (LGPD)
  ehGestor = computed(() => ['ADMIN', 'GERENTE'].includes(this.auth.usuario()?.perfil ?? ''));
  editavel = computed(() => this.painel() === 'nova' || this.selecionada()?.status === 'ATIVA');

  // Sempre que mudar a data, a hora ou o número de pessoas, busca as mesas que servem
  private buscaDeMesas = this.form.valueChanges
    .pipe(
      debounceTime(250),
      filter(() => this.editavel()),
      map(() => this.horarioEscolhido()),
      filter((horario) => horario !== null),
      distinctUntilChanged((a, b) => a?.inicio === b?.inicio && a?.pessoas === b?.pessoas),
      switchMap((horario) => {
        this.buscandoMesas.set(true);
        return this.mesasApi.disponiveis(horario!.inicio, horario!.pessoas, this.selecionada()?.id).pipe(catchError(() => of([] as Mesa[])));
      }),
      takeUntilDestroyed(),
    )
    .subscribe((mesas) => {
      this.buscandoMesas.set(false);
      this.mesasLivres.set(mesas);
      const escolhida = this.form.controls.mesaId.value;
      if (!mesas.some((m) => m.id === escolhida)) this.form.controls.mesaId.setValue(0);
    });

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erroDaLista.set('');
    const inicioDoDia = new Date(`${this.dia()}T00:00`);
    const fimDoDia = new Date(inicioDoDia);
    fimDoDia.setDate(fimDoDia.getDate() + 1);

    this.api.listar(inicioDoDia.toISOString(), fimDoDia.toISOString()).subscribe({
      next: (lista) => {
        this.reservas.set(lista);
        this.carregando.set(false);
      },
      error: (erro: HttpErrorResponse) => {
        this.erroDaLista.set(mensagemDoErro(erro));
        this.carregando.set(false);
      },
    });
  }

  mudarDia(dias: number): void {
    const data = new Date(`${this.dia()}T00:00`);
    data.setDate(data.getDate() + dias);
    this.escolherDia(dataLocal(data));
  }

  escolherDia(dia: string): void {
    if (!dia) return;
    this.dia.set(dia);
    this.fechar();
    this.carregar();
  }

  hoje(): void {
    this.escolherDia(dataLocal(new Date()));
  }

  nova(): void {
    this.selecionada.set(null);
    this.limparPainel();
    this.form.enable();
    this.form.reset({ nomeCliente: '', telefone: '', qtdPessoas: 2, data: this.dia(), hora: '', mesaId: 0 });
    this.mesasLivres.set([]);
    this.painel.set('nova');
    rolarAteAGaveta();
  }

  editar(reserva: Reserva): void {
    this.selecionada.set(reserva);
    this.limparPainel();
    this.form.enable();
    const inicio = new Date(reserva.inicio);
    this.form.reset({
      nomeCliente: reserva.nomeCliente,
      telefone: reserva.telefone ? this.formatarTelefone(reserva.telefone) : '',
      qtdPessoas: reserva.qtdPessoas,
      data: dataLocal(inicio),
      hora: horaLocal(inicio),
      mesaId: reserva.mesaId,
    });
    // O garçom não vê o telefone: o campo fica de fora e o servidor mantém o que já estava salvo
    if (!this.ehGestor()) this.form.controls.telefone.disable();
    if (reserva.status !== 'ATIVA') this.form.disable();
    this.mesasLivres.set([]);
    this.painel.set('editar');
    if (reserva.status === 'ATIVA') this.buscarMesasDaReserva(reserva);
    rolarAteAGaveta();
  }

  fechar(): void {
    this.painel.set('fechado');
    this.selecionada.set(null);
    this.limparPainel();
  }

  salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const horario = this.horarioEscolhido();
    if (!horario) return;

    const valores = this.form.getRawValue();
    const dados = {
      mesaId: valores.mesaId,
      nomeCliente: valores.nomeCliente,
      telefone: valores.telefone,
      qtdPessoas: valores.qtdPessoas,
      inicio: horario.inicio,
    };
    const atual = this.selecionada();

    this.salvando.set(true);
    this.erroDoPainel.set('');
    const chamada = this.painel() === 'nova' ? this.api.criar(dados) : this.api.atualizar(atual!.id, dados);
    chamada.subscribe({
      next: () => this.terminar(),
      error: (erro: HttpErrorResponse) => this.falhar(erro),
    });
  }

  // Só PIX ou cartão; o valor vira crédito na primeira comanda quando o cliente chegar
  anteciparPagamento(): void {
    const reserva = this.selecionada();
    const valor = this.valorAntecipado();
    if (!reserva) return;
    if (valor === null || valor <= 0) {
      this.erroDoPainel.set('Informe o valor pago antecipadamente.');
      return;
    }
    this.salvando.set(true);
    this.erroDoPainel.set('');
    this.caixaApi.anteciparReserva(reserva.id, this.metodoAntecipado(), valor).subscribe({
      next: () => {
        this.valorAntecipado.set(null);
        this.terminar();
      },
      error: (erro: HttpErrorResponse) => this.falhar(erro),
    });
  }

  numero(evento: Event): number | null {
    const texto = (evento.target as HTMLInputElement).value;
    return texto === '' ? null : Number(texto);
  }

  reais(valor: number): string {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(valor);
  }

  cancelarReserva(): void {
    const reserva = this.selecionada();
    if (!reserva) return;
    this.salvando.set(true);
    this.api.cancelar(reserva.id).subscribe({
      next: () => this.terminar(),
      error: (erro: HttpErrorResponse) => this.falhar(erro),
    });
  }

  registrarChegada(): void {
    const reserva = this.selecionada();
    if (!reserva) return;
    this.salvando.set(true);
    this.api.registrarChegada(reserva.id).subscribe({
      next: () => this.terminar(),
      error: (erro: HttpErrorResponse) => this.falhar(erro),
    });
  }

  // O banco guarda só os números; na tela aparece como (11) 91234-5678
  // Para o garçom o telefone sempre vem vazio, então só gestor vê a frase
  telefoneRemovido(r: Reserva): boolean {
    return this.ehGestor() && !r.telefone && r.status !== 'ATIVA' && r.status !== 'CHEGOU';
  }

  formatarTelefone(digitos: string): string {
    const numero = digitos.slice(2);
    const corte = numero.length - 4;
    return `(${digitos.slice(0, 2)}) ${numero.slice(0, corte)}-${numero.slice(corte)}`;
  }

  invalido(campo: 'nomeCliente' | 'telefone' | 'qtdPessoas' | 'data' | 'hora' | 'mesaId'): boolean {
    const controle = this.form.controls[campo];
    return controle.invalid && controle.touched;
  }

  private terminar(): void {
    this.fechar();
    this.carregar();
  }

  private falhar(erro: HttpErrorResponse): void {
    this.salvando.set(false);
    this.confirmandoCancelar.set(false);
    this.erroDoPainel.set(mensagemDoErro(erro));
  }

  // Data e hora do formulário no formato que a API espera (instante em UTC)
  private horarioEscolhido(): { inicio: string; pessoas: number } | null {
    const { data, hora, qtdPessoas } = this.form.getRawValue();
    if (!data || !hora || !qtdPessoas || qtdPessoas < 1) return null;
    const inicio = new Date(`${data}T${hora}`);
    return Number.isNaN(inicio.getTime()) ? null : { inicio: inicio.toISOString(), pessoas: qtdPessoas };
  }

  private buscarMesasDaReserva(reserva: Reserva): void {
    this.mesasApi.disponiveis(reserva.inicio, reserva.qtdPessoas, reserva.id).subscribe({
      next: (mesas) => this.mesasLivres.set(mesas),
      error: () => this.mesasLivres.set([]),
    });
  }

  private limparPainel(): void {
    this.erroDoPainel.set('');
    this.salvando.set(false);
    this.confirmandoCancelar.set(false);
  }
}
