import { provideHttpClient } from '@angular/common/http';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';

import { ConexaoStatus } from '../interceptors/retry-get.interceptor';
import { StatusLoja, StatusLojaResponse, StatusLojaService } from './status-loja.service';

/** Dobra do EventSource: guarda as instâncias para o teste simular queda e evento. */
class FakeEventSource {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 2;
  static instancias: FakeEventSource[] = [];

  readyState = FakeEventSource.CONNECTING;
  onerror: (() => void) | null = null;
  onopen: (() => void) | null = null;
  private readonly listeners = new Map<string, (e: MessageEvent) => void>();

  constructor(readonly url: string) {
    FakeEventSource.instancias.push(this);
  }

  addEventListener(tipo: string, fn: (e: MessageEvent) => void): void {
    this.listeners.set(tipo, fn);
  }

  emitir(tipo: string, dados: unknown): void {
    this.listeners.get(tipo)?.({ data: JSON.stringify(dados) } as MessageEvent);
  }

  fecharPorErro(): void {
    this.readyState = FakeEventSource.CLOSED;
    this.onerror?.();
  }

  close(): void {
    this.readyState = FakeEventSource.CLOSED;
  }
}

describe('StatusLojaService.conectarStream', () => {
  const original = window.EventSource;

  beforeEach(() => {
    FakeEventSource.instancias = [];
    (window as unknown as { EventSource: unknown }).EventSource = FakeEventSource;
    TestBed.configureTestingModule({ providers: [provideHttpClient()] });
  });

  afterEach(() => {
    window.EventSource = original;
  });

  it('abre uma conexão nova quando o EventSource desiste (ex.: 502 no deploy)', fakeAsync(() => {
    const recebidos: StatusLojaResponse[] = [];
    const sub = TestBed.inject(StatusLojaService).conectarStream().subscribe(s => recebidos.push(s));

    FakeEventSource.instancias[0].fecharPorErro();
    tick(5000);

    expect(FakeEventSource.instancias.length).toBe(2);
    const pausada = { status: StatusLoja.PAUSADA, mensagem: 'pausa', numeroSessao: 1 };
    FakeEventSource.instancias[1].emitir('status', pausada);
    expect(recebidos).toEqual([pausada]);

    sub.unsubscribe();
    expect(FakeEventSource.instancias[1].readyState).toBe(FakeEventSource.CLOSED);
  }));

  it('conexão reaberta desliga o aviso "Reconectando…"', () => {
    const conexao = TestBed.inject(ConexaoStatus);
    conexao.reconectando.set(true);
    const sub = TestBed.inject(StatusLojaService).conectarStream().subscribe();

    FakeEventSource.instancias[0].onopen?.();

    expect(conexao.reconectando()).toBeFalse();
    sub.unsubscribe();
  });
});
