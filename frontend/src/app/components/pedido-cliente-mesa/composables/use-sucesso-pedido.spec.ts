import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';

import { useSucessoPedido } from './use-sucesso-pedido';

describe('useSucessoPedido', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const statusUrl = (url: string) => url.startsWith('/api/public/mesa/pedido/p1/status');

  it('encerra como não confirmado quando o pedido sai da fila (rejeitado → 404)', fakeAsync(() => {
    const sucesso = TestBed.runInInjectionContext(() => useSucessoPedido());

    sucesso.iniciarAcompanhamento('p1');
    http.expectOne(r => statusUrl(r.url)).flush({
      pedidoId: 'p1', status: 'AGUARDANDO_ACEITACAO', statusDescricao: 'Aguardando',
      dataHoraSolicitacao: '', tempoEsperaSegundos: 0,
    });

    tick(100 + 5000);
    http.expectOne(r => statusUrl(r.url)).flush(null, { status: 404, statusText: 'Not Found' });

    expect(sucesso.pedidoCancelado()).toBeTrue();
    expect(sucesso.statusPedido()?.motivoCancelamento).toContain('Fale com um atendente');

    tick(15000);
    http.expectNone(() => true); // polling parou
    discardPeriodicTasks();
  }));
});
