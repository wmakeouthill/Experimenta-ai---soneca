import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NgZone } from '@angular/core';
import { Subscription } from 'rxjs';
import { ConexaoStatus, retryGetInterceptor } from './retry-get.interceptor';

describe('retryGetInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let conexao: ConexaoStatus;

  const falhar = (url: string, status: number) =>
    backend.expectOne(url).flush(null, { status, statusText: 'erro' });

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([retryGetInterceptor])), provideHttpClientTesting()]
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    conexao = TestBed.inject(ConexaoStatus);
  });

  afterEach(() => backend.verify());

  it('refaz GET em 503 e mostra "reconectando" até o servidor responder', fakeAsync(() => {
    let resposta: unknown;
    http.get('/api/pedidos').subscribe(r => (resposta = r));

    falhar('/api/pedidos', 503);
    expect(conexao.reconectando()).toBeTrue();

    tick(1000);
    backend.expectOne('/api/pedidos').flush([]);

    expect(resposta).toEqual([]);
    expect(conexao.reconectando()).toBeFalse();
  }));

  it('polling fora da zona: liga o aviso dentro da zona e o cancelamento do ciclo não o desliga', fakeAsync(() => {
    const zone = TestBed.inject(NgZone);
    spyOn(zone, 'run').and.callThrough();
    let ciclo!: Subscription;
    zone.runOutsideAngular(() => (ciclo = http.get('/api/pedidos').subscribe()));

    falhar('/api/pedidos', 502);
    expect(zone.run).toHaveBeenCalled();
    expect(conexao.reconectando()).toBeTrue();

    ciclo.unsubscribe(); // switchMap do próximo tick
    expect(conexao.reconectando()).toBeTrue();

    http.get('/api/pedidos').subscribe();
    backend.expectOne('/api/pedidos').flush([]);
    expect(conexao.reconectando()).toBeFalse();
  }));

  it('não refaz POST nem GET com 429', fakeAsync(() => {
    let erros = 0;
    http.post('/api/pedidos', {}).subscribe({ error: () => erros++ });
    falhar('/api/pedidos', 503);
    http.get('/api/produtos').subscribe({ error: () => erros++ });
    falhar('/api/produtos', 429);

    tick(20000);
    backend.expectNone(() => true);
    expect(erros).toBe(2);
    expect(conexao.reconectando()).toBeFalse();
  }));
});
