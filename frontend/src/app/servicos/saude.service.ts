import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';

export type StatusApi = 'ok' | 'banco-indisponivel' | 'api-offline';

export interface EstadoSaude {
  status: StatusApi;
  banco: boolean;
  versao: string;
  horario: string;
}

@Injectable({ providedIn: 'root' })
export class SaudeService {
  private http = inject(HttpClient);

  estado = signal<EstadoSaude | null>(null);

  atualizar(): void {
    this.http.get<EstadoSaude>('/api/v1/saude').subscribe({
      next: (resposta) => this.estado.set(resposta),
      error: (erro: HttpErrorResponse) => {
        // Com 503 a API responde dizendo que o banco caiu. Sem resposta, a API está desligada.
        if (erro.status === 503 && erro.error) {
          this.estado.set(erro.error as EstadoSaude);
        } else {
          this.estado.set({ status: 'api-offline', banco: false, versao: '', horario: new Date().toISOString() });
        }
      },
    });
  }
}
