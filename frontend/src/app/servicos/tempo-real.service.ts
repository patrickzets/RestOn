import { Injectable } from '@angular/core';
import { EMPTY, Observable } from 'rxjs';

// Escuta os avisos do servidor (SSE). Cada aviso significa "algo mudou": a tela recarrega os dados
// pela API normal. Quando a conexão cai e volta (Wi-Fi do salão), também avisa, para não perder nada.
@Injectable({ providedIn: 'root' })
export class TempoRealService {
  ouvir(canal: 'pedidos' | 'mesas'): Observable<void> {
    if (typeof EventSource === 'undefined') return EMPTY;

    return new Observable<void>((inscrito) => {
      const conexao = new EventSource(`/api/v1/stream/${canal}`);
      const avisar = () => inscrito.next();

      conexao.addEventListener('mudou', avisar);
      conexao.addEventListener('conectado', avisar);
      // O navegador reconecta sozinho; ao reconectar chega um novo "conectado"
      return () => conexao.close();
    });
  }
}
