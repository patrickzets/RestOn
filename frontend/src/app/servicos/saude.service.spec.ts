import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SaudeService } from './saude.service';

describe('SaudeService', () => {
  let servico: SaudeService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servico = TestBed.inject(SaudeService);
    http = TestBed.inject(HttpTestingController);
  });

  it('guarda o estado quando a API responde ok', () => {
    servico.atualizar();
    http.expectOne('/api/v1/saude').flush({ status: 'ok', banco: true, versao: '0.0.1', horario: '2026-11-05T12:00:00Z' });

    expect(servico.estado()?.status).toBe('ok');
    expect(servico.estado()?.banco).toBe(true);
  });

  it('guarda banco-indisponivel quando a API responde 503', () => {
    servico.atualizar();
    http
      .expectOne('/api/v1/saude')
      .flush({ status: 'banco-indisponivel', banco: false, versao: '0.0.1', horario: '' }, { status: 503, statusText: 'Service Unavailable' });

    expect(servico.estado()?.status).toBe('banco-indisponivel');
  });

  it('guarda api-offline quando não há resposta', () => {
    servico.atualizar();
    http.expectOne('/api/v1/saude').error(new ProgressEvent('error'));

    expect(servico.estado()?.status).toBe('api-offline');
  });
});
